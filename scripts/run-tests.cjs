// Run Tests: runs each suite, one Run Tests at a time, and writes each suite's scored results file
// from this run only. A candidate's own test runs never take the lock and never write a scored file.
const fs = require("fs");
const path = require("path");
const { spawn } = require("child_process");

const ROOT = path.resolve(__dirname, "..");
const LOCK_FILE = path.join(ROOT, ".cache/test-run.lock");
const TAKEOVER_FILE = `${LOCK_FILE}.takeover`;
// Gradle runs the tests in a JVM that its daemon starts, outside the suite's process group. The
// scoring build gives that JVM its own work folder, which is how Run Tests finds it without ever
// touching a test run started from the terminal.
const SCORING_WORKER_ARG = `-Dorg.gradle.internal.worker.tmpdir=${path.join(ROOT, "backend/build/scoring/tmp/test/work")}`;
// One entry per suite, each with the scored file it owns. `command` and `env` point the suite's
// reporter at this run's own file, which is renamed into place once every suite has finished.
const SUITES = [
    {
        name: "Backend",
        cwd: "backend",
        scoredFile: "output/backend.xml",
        command: (runFile, leftOut) => [
            "./gradlew", "test", "-Pscoring", "--project-cache-dir", "../.cache/gradle-scoring", "--console=plain",
            ...(leftOut.length > 0 ? [`-PskipTestSources=${leftOut.join(",")}`] : []),
        ],
        env: (runFile) => ({ JUNIT_REPORT_FILE: runFile }),
        outsideGroup: () => pidsWithArg(SCORING_WORKER_ARG),
        testSourcesThatFailedToCompile,
    },
    {
        name: "Frontend",
        cwd: "frontend",
        scoredFile: "output/frontend.xml",
        command: () => ["bun", "run", "test"],
        env: (runFile) => ({ VITEST_JUNIT_FILE: runFile }),
    },
];
const RUN_FILE_PATTERN = /^junit\.run-\d+-\d+\.xml$/;
// Only a question's hidden scoring files carry this list of every testcase its solution reports. A test whose
// file could not load or compile on this code is then scored as failed, instead of dropping out of the results.
const EXPECTED_TESTCASES_FILE = path.join(ROOT, "scoring/expected-testcases.json");
const DID_NOT_RUN_MESSAGE = "did not run: its test file failed to load or compile on this code";
// A suite that wrote no results at all gets one failed testcase in its scored file, so the file can't just go missing
// and leave the score to the other suite.
const ENDED_EARLY_REASON = "wrote no results: the code did not compile, the test process ended before it reported, or Run Tests reached its time limit. See the Run Tests output.";
const TESTCASE_TAG = /<testcase\b((?:\s+[\w:.-]+="[^"]*")*)\s*\/?>/g;
// A test class left out can stop another one compiling (a shared helper), so the backend can be rerun more than once.
const MAX_COMPILE_RERUNS = 3;
const LOCK_POLL_MS = 1000;
// One limit for the whole Run Tests, time spent waiting for another run included.
const RUN_LIMIT_MS = 300_000;
const KILL_GRACE_MS = 10_000;
const GROUP_POLL_MS = 100;
const WATCHDOG_POLL_MS = 1000;
const WATCHDOG_FLAG = "--watchdog";
const FAILED_EXIT_CODE = 1;
const INTERRUPTED_EXIT_CODE = 130;

let activeSuite = null;
let isLockHeld = false;
let isInterrupted = false;
const runDeadline = Date.now() + RUN_LIMIT_MS;

function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

function pidAlive(pid) {
    try {
        process.kill(pid, 0);
        return true;
    } catch (err) {
        return err.code === "EPERM";
    }
}

// The processes whose command line holds `arg` exactly. Reads /proc, so it finds nothing elsewhere.
function pidsWithArg(arg) {
    let entries;
    try {
        entries = fs.readdirSync("/proc");
    } catch {
        return [];
    }
    return entries
        .filter((entry) => /^\d+$/.test(entry))
        .map(Number)
        .filter((pid) => {
            try {
                return fs.readFileSync(`/proc/${pid}/cmdline`, "utf8").split("\0").includes(arg);
            } catch {
                return false;
            }
        });
}

function killPids(pids, signal) {
    for (const pid of pids) {
        try {
            process.kill(pid, signal);
        } catch (err) {
            if (err.code !== "ESRCH") throw err;
        }
    }
}

function outsideGroupPids(suiteIndex) {
    const suites = suiteIndex === undefined ? SUITES : [SUITES[suiteIndex]].filter(Boolean);
    return suites.flatMap((suite) => (suite.outsideGroup ? suite.outsideGroup() : []));
}

function stopGroup(groupId, signal) {
    try {
        process.kill(-groupId, signal);
    } catch (err) {
        if (err.code !== "ESRCH") throw err;
    }
}

// The owner can free the lock between our failed create and this read. null means "try again".
function readLockOwner() {
    try {
        return fs.readFileSync(LOCK_FILE, "utf8").split(" ").map(Number);
    } catch (err) {
        if (err.code === "ENOENT") return null;
        throw err;
    }
}

// Only one waiting run at a time may clear a dead run's lock. Without this, two waiting runs can
// both clear it, and the second one deletes the lock the first one has just taken.
// Returns false when this run should wait and look again.
function clearDeadLock(deadPid, suiteGroup) {
    if (!tryTakeTakeover()) return false;
    try {
        const owner = readLockOwner();
        if (owner && owner[0] === deadPid) {
            // The previous run was killed before it could release the lock. Stop the suite it left
            // running, so two runs never write their results at the same time.
            if (suiteGroup) stopGroup(suiteGroup, "SIGKILL");
            killPids(outsideGroupPids(), "SIGKILL");
            fs.rmSync(LOCK_FILE, { force: true });
        }
        return true;
    } finally {
        fs.rmSync(TAKEOVER_FILE, { force: true });
    }
}

function tryTakeTakeover() {
    try {
        fs.writeFileSync(TAKEOVER_FILE, String(process.pid), { flag: "wx" });
        return true;
    } catch (err) {
        if (err.code !== "EEXIST") throw err;
    }
    // A run killed while it held the takeover lock leaves it behind. Clear it and look again next time.
    try {
        if (!pidAlive(Number(fs.readFileSync(TAKEOVER_FILE, "utf8")))) fs.rmSync(TAKEOVER_FILE, { force: true });
    } catch (err) {
        if (err.code !== "ENOENT") throw err;
    }
    return false;
}

function tryTakeLock() {
    try {
        fs.writeFileSync(LOCK_FILE, String(process.pid), { flag: "wx" });
        isLockHeld = true;
        return true;
    } catch (err) {
        if (err.code !== "EEXIST") throw err;
    }
    const owner = readLockOwner();
    if (!owner) return tryTakeLock();
    const [ownerPid, suiteGroup] = owner;
    if (pidAlive(ownerPid)) return false;
    if (!clearDeadLock(ownerPid, suiteGroup)) return false;
    return tryTakeLock();
}

// Returns false when the Run Tests limit passes before the other run frees the lock.
async function takeLock() {
    fs.mkdirSync(path.dirname(LOCK_FILE), { recursive: true });
    let isAnnounced = false;
    while (!tryTakeLock()) {
        if (Date.now() >= runDeadline) return false;
        if (!isAnnounced) {
            console.log("Waiting for another test run to finish...");
            isAnnounced = true;
        }
        await sleep(LOCK_POLL_MS);
    }
    return true;
}

function releaseLock() {
    try {
        if (Number(fs.readFileSync(LOCK_FILE, "utf8").split(" ")[0]) === process.pid) fs.rmSync(LOCK_FILE);
    } catch (err) {
        if (err.code !== "ENOENT") throw err;
    }
    isLockHeld = false;
}

// The suite's first process can exit before the ones it started (a forked test JVM). Wait for
// the whole group, and for the suite's processes outside it, so the next run never starts while
// this one is still shutting down.
async function waitForGroupExit(groupId, suiteIndex) {
    const killAt = Date.now() + KILL_GRACE_MS;
    const giveUpAt = killAt + KILL_GRACE_MS;
    while ((pidAlive(-groupId) || outsideGroupPids(suiteIndex).length > 0) && Date.now() < giveUpAt) {
        if (Date.now() >= killAt) {
            stopGroup(groupId, "SIGKILL");
            killPids(outsideGroupPids(suiteIndex), "SIGKILL");
        }
        await sleep(GROUP_POLL_MS);
    }
}

function stopSuite(suite, signal) {
    suite.isStopping = true;
    stopGroup(suite.pid, signal);
    setTimeout(() => stopGroup(suite.pid, "SIGKILL"), KILL_GRACE_MS).unref();
}

// The suite outlives this runner when the runner is killed outright (the IDE closing the Test
// terminal can do that), so a separate process watches it. It runs this same file with
// WATCHDOG_FLAG, in its own session, so nothing that stops the runner reaches it.
function startWatchdog(suiteGroup, suiteIndex) {
    const args = [__filename, WATCHDOG_FLAG, process.pid, suiteGroup, runDeadline, suiteIndex].map(String);
    const watchdog = spawn(process.execPath, args, { stdio: "ignore", detached: true });
    // Without a watchdog the run still works, as it did before there was one.
    watchdog.on("error", () => {});
    return watchdog;
}

// Waits for the watchdog to exit, so a finished run never leaves it behind.
function stopWatchdog(watchdog) {
    if (!watchdog.pid || watchdog.exitCode !== null || watchdog.signalCode !== null) return Promise.resolve();
    return new Promise((resolve) => {
        watchdog.once("exit", resolve);
        watchdog.kill();
    });
}

// Stops the suite's group when the runner is gone, or one poll after the run's limit (the runner
// stops it at the limit itself while it can, and says so). It signals only that group, and exits
// once the group is gone.
async function runWatchdog(runnerPid, suiteGroup, deadline, suiteIndex) {
    if (!Number.isInteger(suiteGroup) || suiteGroup <= 1) return;
    // The runner is this process's parent, so a reused pid can't make a dead runner look alive.
    const isRunnerGone = () => process.ppid !== runnerPid || !pidAlive(runnerPid);
    while (pidAlive(-suiteGroup)) {
        if (isRunnerGone() || Date.now() >= deadline + WATCHDOG_POLL_MS) {
            stopGroup(suiteGroup, "SIGTERM");
            await waitForGroupExit(suiteGroup, suiteIndex);
            return;
        }
        await sleep(WATCHDOG_POLL_MS);
    }
}

function runFileFor(suite, index) {
    return path.join(ROOT, path.dirname(suite.scoredFile), `junit.run-${process.pid}-${index}.xml`);
}

// Each suite runs in its own process group, so a time-out or Ctrl+C also stops the test
// runner's own child processes (a forked JVM, Jest workers). A suite that can leave out tests that do not
// compile has its output passed through and kept, so the runner can read the compiler errors.
function runSuite(suite, index, runFile, leftOut = []) {
    if (Date.now() >= runDeadline) {
        console.error(`${suite.name} tests did not start: Run Tests reached its ${RUN_LIMIT_MS / 60_000}-minute limit.`);
        return Promise.resolve({ code: FAILED_EXIT_CODE, output: "" });
    }
    return new Promise((resolve) => {
        const [command, ...args] = suite.command(runFile, leftOut);
        const keepsOutput = Boolean(suite.testSourcesThatFailedToCompile);
        const child = spawn(command, args, {
            cwd: path.join(ROOT, suite.cwd),
            env: { ...process.env, ...(suite.env ? suite.env(runFile) : {}) },
            stdio: keepsOutput ? ["inherit", "pipe", "pipe"] : "inherit",
            detached: true,
        });
        let output = "";
        if (keepsOutput) {
            for (const [stream, target] of [[child.stdout, process.stdout], [child.stderr, process.stderr]]) {
                stream.on("data", (chunk) => {
                    target.write(chunk);
                    output += chunk;
                });
            }
        }
        activeSuite = child;
        fs.writeFileSync(LOCK_FILE, `${process.pid} ${child.pid}`);
        const watchdog = child.pid ? startWatchdog(child.pid, index) : null;
        const limit = setTimeout(() => {
            console.error(`${suite.name} tests were stopped at the ${RUN_LIMIT_MS / 60_000}-minute Run Tests limit.`);
            stopSuite(child, "SIGTERM");
        }, runDeadline - Date.now());
        child.on("error", (err) => {
            console.error(`Could not start the ${suite.name} tests: ${err.message}`);
        });
        // A suite that never started, or was stopped by a signal, has no exit code of its own.
        child.on("close", async (code) => {
            clearTimeout(limit);
            await waitForGroupExit(child.pid, index);
            if (watchdog) await stopWatchdog(watchdog);
            activeSuite = null;
            resolve({ code: Number.isInteger(code) && code >= 0 ? code : FAILED_EXIT_CODE, output });
        });
    });
}

// The test sources javac reported errors in, relative to src/test/java, when it was the test compile that failed.
// An error in the app's own code is not a test to leave out: then no backend test can run.
function testSourcesThatFailedToCompile(output) {
    if (!output.includes("Execution failed for task ':compileTestJava'")) return [];
    const sources = [...output.matchAll(/\/src\/test\/java\/([^\s:]+\.java):\d+: error:/g)].map((match) => match[1]);
    return [...new Set(sources)];
}

// A test class that does not compile on this code stops every backend test from running. Leave those classes out
// and run the rest, so the tests that do compile still count. The run still fails: those classes did not run.
async function runSuiteLeavingOutBrokenTests(suite, index, runFile) {
    const leftOut = [];
    for (let rerun = 0; ; rerun++) {
        const { code, output } = await runSuite(suite, index, runFile, leftOut);
        const broken = code !== 0 && suite.testSourcesThatFailedToCompile && !isInterrupted
            ? suite.testSourcesThatFailedToCompile(output).filter((source) => !leftOut.includes(source))
            : [];
        if (broken.length === 0 || rerun === MAX_COMPILE_RERUNS) return leftOut.length > 0 ? FAILED_EXIT_CODE : code;
        leftOut.push(...broken);
        console.log(`\n${suite.name}: ${broken.length} test file(s) do not compile on this code. Running the other tests without them:`);
        for (const source of broken) console.log(`  left out: ${source}`);
    }
}

const XML_ENTITIES = { amp: "&", lt: "<", gt: ">", quot: '"', apos: "'" };

function decodeXml(text) {
    return text.replace(/&(#x[0-9a-f]+|#\d+|[a-z]+);/gi, (entity, body) => {
        if (body[0] !== "#") return XML_ENTITIES[body] ?? entity;
        return String.fromCodePoint(body[1].toLowerCase() === "x" ? parseInt(body.slice(2), 16) : Number(body.slice(1)));
    });
}

function encodeXml(text) {
    return String(text).replace(/[&<>"']/g, (char) => `&${Object.keys(XML_ENTITIES).find((key) => XML_ENTITIES[key] === char)};`);
}

function testcaseAttributes(attributeText) {
    return Object.fromEntries([...attributeText.matchAll(/([\w:.-]+)="([^"]*)"/g)].map(([, key, value]) => [key, decodeXml(value)]));
}

// Some reporters name a testcase's class after its describe block, which two files can share, so a file
// attribute, when there is one, is part of the key.
function testcaseKey({ file = "", classname = "", name = "" }) {
    return [file, classname, name].join("\0");
}

function didNotRunSuite(testcases) {
    const failure = `<failure message="${encodeXml(DID_NOT_RUN_MESSAGE)}" type="DidNotRun">${encodeXml(DID_NOT_RUN_MESSAGE)}</failure>`;
    const cases = testcases.map(({ file, classname, name }) => {
        const fileAttribute = file ? ` file="${encodeXml(file)}"` : "";
        return `    <testcase name="${encodeXml(name)}" classname="${encodeXml(classname)}"${fileAttribute} time="0">\n      ${failure}\n    </testcase>\n`;
    });
    return `  <testsuite name="Tests that did not run" tests="${testcases.length}" failures="${testcases.length}" errors="0" skipped="0" time="0">\n${cases.join("")}  </testsuite>\n`;
}

// Vitest reports a test file that cannot load as one failed testcase named after the file. When the list names
// that file's own tests, they are added as "did not run" instead, so the stand-in goes: one row per expected test.
function withoutLoadFailureStandIns(xml, classnames) {
    let removed = 0;
    const kept = xml.replace(/<testsuite\b[^>]*>[\s\S]*?<\/testsuite>\s*/g, (suite) => {
        const cases = [...suite.matchAll(TESTCASE_TAG)].map((match) => testcaseAttributes(match[1]));
        const isStandIn = cases.length === 1 && cases[0].name === cases[0].classname && classnames.has(cases[0].classname);
        if (!isStandIn) return suite;
        removed += 1;
        return "";
    });
    return { xml: kept, removed };
}

// Adds each expected testcase that is missing from its scored file as a failed testcase, in the same file, and
// leaves every testcase that ran as it is. Returns how many were added.
function addTestcasesThatDidNotRun() {
    let expected;
    try {
        expected = JSON.parse(fs.readFileSync(EXPECTED_TESTCASES_FILE, "utf8"));
    } catch (err) {
        if (err.code === "ENOENT") return 0;
        throw err;
    }
    let added = 0;
    for (const [scoredFile, testcases] of Object.entries(expected.files)) {
        const file = path.join(ROOT, scoredFile);
        const read = fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
        const ran = new Set([...read.matchAll(TESTCASE_TAG)].map((match) => testcaseKey(testcaseAttributes(match[1]))));
        const missing = testcases.filter((testcase) => !ran.has(testcaseKey(testcase)));
        if (missing.length === 0) continue;
        const { xml, removed } = withoutLoadFailureStandIns(read, new Set(missing.map(({ classname }) => classname)));
        const suite = didNotRunSuite(missing);
        const end = xml.lastIndexOf("</testsuites>");
        const body = xml.replace(/^\s*<\?xml[^>]*\?>\s*/, "");
        const next = end >= 0
            ? xml.slice(0, end) + suite + xml.slice(end)
            : `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>\n${body}${suite}</testsuites>\n`;
        // Vitest's root element carries totals; keep them in step with the testcases.
        const change = missing.length - removed;
        const withTotals = next.replace(/<testsuites\b[^>]*>/, (tag) => tag
            .replace(/\btests="(\d+)"/, (_, count) => `tests="${Number(count) + change}"`)
            .replace(/\bfailures="(\d+)"/, (_, count) => `failures="${Number(count) + change}"`));
        const temporary = `${file}.did-not-run`;
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(temporary, withTotals);
        fs.renameSync(temporary, file);
        added += missing.length;
    }
    return added;
}

// Only the lock holder runs this, so every run file left here belongs to a killed run.
function removeLeftoverRunFiles() {
    for (const suite of SUITES) {
        const dir = path.join(ROOT, path.dirname(suite.scoredFile));
        for (const name of fs.readdirSync(dir)) {
            if (RUN_FILE_PATTERN.test(name)) fs.rmSync(path.join(dir, name), { force: true });
        }
    }
}

// Each scored file is renamed into place, so it never holds half of a run's results. A suite that
// wrote no results publishes nothing here; addEndedEarlyTestcases() then gives it one failed testcase.
function publishResults(runFiles) {
    for (const [index, suite] of SUITES.entries()) {
        const runFile = runFiles[index];
        if (runFile && fs.existsSync(runFile)) fs.renameSync(runFile, path.join(ROOT, suite.scoredFile));
    }
}

function endedEarlyFile(suite) {
    const name = `${suite.name} tests ended early`;
    const message = `${suite.name} tests ${ENDED_EARLY_REASON}`;
    const failure = `<failure message="${encodeXml(message)}" type="EndedEarly">${encodeXml(message)}</failure>`;
    return `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites tests="1" failures="1">\n`
        + `  <testsuite name="${encodeXml(name)}" tests="1" failures="1" errors="0" skipped="0" time="0">\n`
        + `    <testcase name="${encodeXml(name)}" classname="${encodeXml(suite.name)}" time="0">\n      ${failure}\n    </testcase>\n`
        + "  </testsuite>\n</testsuites>\n";
}

// Returns the names of the suites that wrote no results and now hold the one failed testcase. A file with no
// testcase in it counts as no results: Gradle still writes its report when the run limit stops the tests.
function addEndedEarlyTestcases() {
    const endedEarly = [];
    for (const suite of SUITES) {
        const file = path.join(ROOT, suite.scoredFile);
        if (fs.existsSync(file) && /<testcase\b/.test(fs.readFileSync(file, "utf8"))) continue;
        const temporary = `${file}.ended-early`;
        fs.writeFileSync(temporary, endedEarlyFile(suite));
        fs.renameSync(temporary, file);
        endedEarly.push(suite.name);
    }
    return endedEarly;
}

// Ctrl+C only asks the suite to stop. main() frees the lock once the suite has really stopped.
function onInterrupt(signal) {
    if (!isLockHeld) process.exit(INTERRUPTED_EXIT_CODE);
    isInterrupted = true;
    if (activeSuite) stopSuite(activeSuite, signal);
}

async function main() {
    process.on("SIGINT", onInterrupt);
    process.on("SIGTERM", onInterrupt);
    // The IDE closing the Test terminal sends SIGHUP. Stop the suite as for SIGTERM.
    process.on("SIGHUP", () => onInterrupt("SIGTERM"));
    if (!(await takeLock())) {
        console.error(`Stopped waiting: another test run held the lock for the whole ${RUN_LIMIT_MS / 60_000}-minute Run Tests limit. Run Tests again.`);
        process.exitCode = FAILED_EXIT_CODE;
        return;
    }
    const runFiles = [];
    try {
        for (const suite of SUITES) {
            fs.mkdirSync(path.join(ROOT, path.dirname(suite.scoredFile)), { recursive: true });
            fs.rmSync(path.join(ROOT, suite.scoredFile), { force: true });
        }
        removeLeftoverRunFiles();
        const exitCodes = [];
        for (const [index, suite] of SUITES.entries()) {
            if (isInterrupted) break;
            const runFile = runFileFor(suite, index);
            runFiles.push(runFile);
            exitCodes.push(await runSuiteLeavingOutBrokenTests(suite, index, runFile));
        }
        if (isInterrupted) {
            process.exitCode = INTERRUPTED_EXIT_CODE;
            return;
        }
        publishResults(runFiles);
        const endedEarly = addEndedEarlyTestcases();
        for (const name of endedEarly) console.error(`\n${name} tests wrote no results. They are scored as one failed test, "${name} tests ended early".`);
        const didNotRun = addTestcasesThatDidNotRun();
        if (didNotRun > 0) console.log(`\n${didNotRun} expected test(s) did not run on this code and are scored as failed.`);
        process.exitCode = endedEarly.length === 0 && didNotRun === 0 && exitCodes.every((code) => code === 0) ? 0 : FAILED_EXIT_CODE;
    } finally {
        for (const runFile of runFiles) fs.rmSync(runFile, { force: true });
        releaseLock();
    }
}

if (process.argv[2] === WATCHDOG_FLAG) {
    runWatchdog(...process.argv.slice(3).map(Number));
} else {
    main();
}
