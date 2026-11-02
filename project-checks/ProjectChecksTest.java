import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;
import org.w3c.dom.Document;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Standalone JUnit checks. Set PROJECT_ROOT or -Dproject.root to the application. */
@DisabledIfEnvironmentVariable(named = "PROJECT_CHECK_ACTIVE", matches = "1")
public class ProjectChecksTest {
  private static final Path ROOT =
      Path.of(System.getProperty("project.root", System.getenv().getOrDefault("PROJECT_ROOT", ".")))
          .toAbsolutePath()
          .normalize();
  private static final ObjectMapper JSON =
      new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
  private static final List<String> REQUIRED =
      List.of(
          "frontend",
          "frontend/src",
          "frontend/src/features",
          "frontend/src/shared",
          "backend",
          "backend/src",
          "backend/src/main",
          "backend/src/main/java",
          "backend/src/main/resources",
          "backend/gradle",
          "backend/gradle/wrapper",
          ".vscode");
  private static final Set<String> EXCLUDED =
      Set.of(
          ".git",
          "project-checks",
          "node_modules",
          ".venv",
          "venv",
          ".gradle",
          ".m2",
          ".cache",
          "__pycache__",
          ".ruff_cache");
  private static final Set<String> GENERATED =
      Set.of(
          "dist",
          "build",
          "coverage",
          "frontend/dist",
          "frontend/build",
          "frontend/coverage",
          "backend/dist",
          "backend/build",
          "backend/coverage",
          "backend/target");
  private static final Set<String> REPORT_ROOTS =
      Set.of("testsuite", "testsuites", "testng-results", "testrun");
  private static final Pattern EXACT_JS =
      Pattern.compile("^[v=]?\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?$");

  private static String relative(Path root, Path filename) {
    return root.relativize(filename).toString().replace('\\', '/');
  }

  private static List<Path> files(Path root, boolean generated) throws Exception {
    List<Path> result = new ArrayList<>();
    Files.walkFileTree(
        root,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
            String name = relative(root, directory);
            return EXCLUDED.contains(directory.getFileName().toString())
                    || (generated && GENERATED.contains(name))
                ? FileVisitResult.SKIP_SUBTREE
                : FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (EXCLUDED.contains(file.getFileName().toString())) return FileVisitResult.CONTINUE;
            assertTrue(
                attributes.isRegularFile(), "Cannot reliably inspect non-regular path: " + file);
            result.add(file);
            return FileVisitResult.CONTINUE;
          }
        });
    result.sort(Comparator.naturalOrder());
    return result;
  }

  private static Path resource(String name) throws Exception {
    return Path.of(ProjectChecksTest.class.getResource("/" + name).toURI());
  }

  private static JsonNode configuration() throws Exception {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    Iterator<Object> documents =
        new Yaml(new SafeConstructor(options))
            .loadAll(Files.readString(ROOT.resolve("hackerrank.yml")))
            .iterator();
    assertTrue(documents.hasNext(), "Configuration is empty");
    JsonNode data = JSON.valueToTree(documents.next());
    assertFalse(documents.hasNext(), "Configuration must contain one YAML document");
    assertNotNull(data, "Configuration is empty");
    var schema =
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4)
            .getSchema(JSON.readTree(resource("project-config.schema.json").toFile()));
    assertTrue(schema.validate(data).isEmpty(), () -> schema.validate(data).toString());
    JsonNode configuration = data.path("configuration");
    for (String name : List.of("install", "run", "test")) {
      if (name.equals("test") && !configuration.path("ide_config").path("project_menu").has(name))
        continue;
      JsonNode value = configuration.path("ide_config").path("project_menu").path(name);
      assertTrue(value.isTextual() && !value.asText().isBlank(), "Missing " + name + " command");
    }
    if (configuration.has("scoring"))
      assertFalse(
          configuration.path("scoring").path("command").asText().isBlank(),
          "Missing scoring command");
    return configuration;
  }

  private static String selectedTestCommand(JsonNode config, Path workspace) throws Exception {
    JsonNode menu = config.path("ide_config").path("project_menu");
    if (menu.has("test")) return menu.path("test").asText();
    JsonNode manifest = JSON.readTree(workspace.resolve("package.json").toFile());
    JsonNode scripts = manifest.path("scripts");
    if (scripts.isMissingNode()) return null;
    assertTrue(scripts.isObject(), "Root package.json scripts must be an object");
    if (!scripts.has("test")) return null;
    assertTrue(
        scripts.path("test").isTextual() && !scripts.path("test").asText().isBlank(),
        "Root package.json scripts.test must be a nonblank string");
    String manager = manifest.path("packageManager").asText("").split("@", 2)[0];
    if (!Set.of("bun", "npm", "pnpm", "yarn").contains(manager)) {
      manager = "npm";
      for (String entry :
          List.of(
              "bun.lock=bun",
              "bun.lockb=bun",
              "pnpm-lock.yaml=pnpm",
              "yarn.lock=yarn",
              "package-lock.json=npm",
              "npm-shrinkwrap.json=npm")) {
        String[] parts = entry.split("=", 2);
        if (Files.isRegularFile(workspace.resolve(parts[0]))) {
          manager = parts[1];
          break;
        }
      }
    }
    return manager + " run test";
  }

  private static Document xml(byte[] bytes) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    var builder = factory.newDocumentBuilder();
    builder.setErrorHandler(
        new DefaultHandler() {
          @Override
          public void error(SAXParseException error) throws SAXParseException {
            throw error;
          }

          @Override
          public void fatalError(SAXParseException error) throws SAXParseException {
            throw error;
          }
        });
    return builder.parse(new ByteArrayInputStream(bytes));
  }

  @Test
  void noExistingXmlReports() throws Exception {
    List<String> found = new ArrayList<>();
    for (Path file : files(ROOT, false)) {
      String name = relative(ROOT, file);
      if (!name.toLowerCase().endsWith(".xml")) continue;
      boolean hint =
          Pattern.compile(
                      "(?i)(?:^|/)(?:test-results|test-reports|surefire-reports|failsafe-reports)/")
                  .matcher(name)
                  .find()
              || Pattern.compile(
                      "(?i)^(?:TEST-|junit)|^test[-_]results\\.xml$|^test[-_]report\\.xml$|^testng-results\\.xml$")
                  .matcher(file.getFileName().toString())
                  .find();
      if (hint
          || REPORT_ROOTS.contains(
              xml(Files.readAllBytes(file)).getDocumentElement().getLocalName().toLowerCase()))
        found.add(name);
    }
    assertTrue(found.isEmpty(), "Existing XML test reports: " + found);
  }

  @Test
  void requiredPaths() throws Exception {
    for (String name : REQUIRED) {
      Path parent = ROOT;
      for (String component : name.split("/")) {
        try (Stream<Path> entries = Files.list(parent)) {
          assertTrue(
              entries.anyMatch(item -> item.getFileName().toString().equals(component)),
              "Missing required path: " + name);
        }
        parent = parent.resolve(component);
        // A regular file may replace the entry and its directory subtree.
        if (Files.isRegularFile(parent)) break;
        assertTrue(Files.isDirectory(parent), "Unsupported entry: " + name);
      }
    }
  }

  @Test
  void rootReadmeExists() throws Exception {
    try (Stream<Path> entries = Files.list(ROOT)) {
      assertTrue(
          entries.anyMatch(
              file ->
                  file.getFileName().toString().equals("README.md") && Files.isRegularFile(file)),
          "Root README.md must exist as a file");
    }
  }

  @Test
  void configurationAndCommands() throws Exception {
    configuration();
  }

  private static boolean pinnedJavaScript(String version) {
    if (EXACT_JS.matcher(version).matches()) return true;
    if (version.startsWith("npm:"))
      return EXACT_JS.matcher(version.substring(version.lastIndexOf('@') + 1)).matches();
    return version.matches("(?s)^(?:git\\+|git:|github:|gitlab:|bitbucket:).*#[a-fA-F0-9]{40}$");
  }

  @Test
  void javascriptDependenciesArePinned() throws Exception {
    Map<Path, JsonNode> manifests = new HashMap<>();
    Map<String, Integer> names = new HashMap<>();
    for (Path file : files(ROOT, true))
      if (file.getFileName().toString().equals("package.json")) {
        JsonNode data = JSON.readTree(file.toFile());
        assertTrue(data != null && data.isObject(), "Invalid manifest: " + file);
        manifests.put(file, data);
        if (data.path("name").isTextual()) names.merge(data.path("name").asText(), 1, Integer::sum);
      }
    assertTrue(
        manifests.containsKey(ROOT.resolve("package.json")), "Root package.json is required");
    List<String> errors = new ArrayList<>();
    for (var manifest : manifests.entrySet())
      for (String section :
          List.of("dependencies", "devDependencies", "optionalDependencies", "peerDependencies")) {
        JsonNode values = manifest.getValue().path(section);
        if (values.isMissingNode()) continue;
        assertTrue(values.isObject(), "Invalid dependency section: " + section);
        Iterator<Map.Entry<String, JsonNode>> entries = values.fields();
        while (entries.hasNext()) {
          var entry = entries.next();
          String version = entry.getValue().isTextual() ? entry.getValue().asText().trim() : "";
          boolean pinned = pinnedJavaScript(version);
          if (version.startsWith("workspace:")) pinned = names.getOrDefault(entry.getKey(), 0) == 1;
          if (version.startsWith("file:") || version.startsWith("link:")) {
            String reference = version.substring(version.indexOf(':') + 1);
            Path target = manifest.getKey().getParent().resolve(reference).normalize();
            pinned =
                !reference.isEmpty()
                    && target.startsWith(ROOT)
                    && Files.exists(target)
                    && target.toRealPath().startsWith(ROOT.toRealPath());
          }
          if (!pinned)
            errors.add(
                relative(ROOT, manifest.getKey())
                    + ": "
                    + section
                    + "."
                    + entry.getKey()
                    + " is not pinned: "
                    + version);
        }
      }
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }

  private static String stripComments(String text) {
    StringBuilder output = new StringBuilder();
    char quote = 0;
    for (int i = 0; i < text.length(); i++) {
      char value = text.charAt(i);
      if (quote != 0) {
        output.append(value);
        if (value == '\\' && i + 1 < text.length()) output.append(text.charAt(++i));
        else if (value == quote) quote = 0;
        continue;
      }
      if (value == '\'' || value == '"') {
        quote = value;
        output.append(value);
        continue;
      }
      if (value == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
        while (i < text.length() && text.charAt(i) != '\n') i++;
        output.append('\n');
        continue;
      }
      if (value == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
        i += 2;
        while (i + 1 < text.length() && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) i++;
        i++;
        output.append(' ');
        continue;
      }
      output.append(value);
    }
    return output.toString();
  }

  private static String resolveVersion(String value, Map<String, String> variables) {
    Matcher matcher = Pattern.compile("\\$\\{([A-Za-z_]\\w*)}|\\$([A-Za-z_]\\w*)").matcher(value);
    StringBuilder output = new StringBuilder();
    while (matcher.find()) {
      String name = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
      matcher.appendReplacement(
          output, Matcher.quoteReplacement(variables.getOrDefault(name, matcher.group())));
    }
    matcher.appendTail(output);
    return output.toString();
  }

  private static boolean pinnedGradle(String version) {
    return !version.isEmpty()
        && !Pattern.compile("[+*\\[\\](),<>=!$\\s]").matcher(version).find()
        && !version.toLowerCase().startsWith("latest")
        && !version.equalsIgnoreCase("release")
        && !version.toLowerCase().endsWith("-snapshot");
  }

  @Test
  void gradleDependenciesArePinned() throws Exception {
    List<Path> manifests =
        files(ROOT, true).stream()
            .filter(
                file ->
                    Set.of("build.gradle", "build.gradle.kts")
                        .contains(file.getFileName().toString()))
            .toList();
    assertFalse(manifests.isEmpty(), "No Gradle dependency declarations found");
    for (Path file : manifests) {
      String text = stripComments(Files.readString(file));
      Map<String, String> variables = new HashMap<>();
      for (Path properties :
          List.of(ROOT.resolve("gradle.properties"), file.getParent().resolve("gradle.properties")))
        if (Files.isRegularFile(properties))
          for (String line : Files.readAllLines(properties)) {
            String[] parts = line.trim().split("=", 2);
            if (parts.length == 2 && !parts[0].startsWith("#"))
              variables.put(parts[0].trim(), parts[1].trim());
          }
      Matcher variable =
          Pattern.compile(
                  "(?m)(?:^|[;{}])\\s*(?:def\\s+|val\\s+|var\\s+)?([A-Za-z_]\\w*)\\s*=\\s*[\"']([^\"']*)[\"']")
              .matcher(text);
      while (variable.find()) variables.put(variable.group(1), variable.group(2));
      Set<String> builtins =
          Set.of(
              "java",
              "java-library",
              "application",
              "base",
              "war",
              "groovy",
              "scala",
              "antlr",
              "jacoco",
              "checkstyle",
              "pmd",
              "idea",
              "eclipse",
              "maven-publish",
              "signing");
      Map<String, String> plugins = new HashMap<>();
      Matcher plugin =
          Pattern.compile(
                  "\\bid\\s*\\(?\\s*[\"']([^\"']+)[\"'](?:\\s*\\))?(?:\\s+version\\s*\\(?\\s*[\"']([^\"']+)[\"'])?")
              .matcher(text);
      while (plugin.find())
        if (!builtins.contains(plugin.group(1))) {
          String version =
              resolveVersion(plugin.group(2) == null ? "" : plugin.group(2), variables);
          assertTrue(
              pinnedGradle(version), "Unpinned or unresolved Gradle plugin: " + plugin.group(1));
          plugins.put(plugin.group(1), version);
        }
      boolean boot =
          pinnedGradle(plugins.getOrDefault("org.springframework.boot", ""))
              && pinnedGradle(plugins.getOrDefault("io.spring.dependency-management", ""));
      Matcher block = Pattern.compile("\\bdependencies\\s*\\{").matcher(text);
      List<String> blocks = new ArrayList<>();
      while (block.find()) {
        int depth = 1, end = block.end();
        char quote = 0;
        for (; end < text.length() && depth > 0; end++) {
          char value = text.charAt(end);
          if (quote != 0) {
            if (value == '\\') end++;
            else if (value == quote) quote = 0;
          } else if (value == '\'' || value == '"') quote = value;
          else if (value == '{') depth++;
          else if (value == '}') depth--;
        }
        assertEquals(0, depth, "Unclosed Gradle dependencies block");
        blocks.add(text.substring(block.end(), end - 1));
      }
      Set<String> managed = new HashSet<>();
      Pattern declarationPattern = Pattern.compile("(?m)(?:^|;)\\s*([A-Za-z_]\\w*)\\b([^\\n;]*)");
      Pattern coordinatePattern =
          Pattern.compile(
              "^\\s*\\(?\\s*(?:(?:platform|enforcedPlatform)\\s*\\(\\s*)?[\"']([^\"']+)[\"']");
      for (String body : blocks) {
        Matcher declaration = declarationPattern.matcher(body);
        while (declaration.find()) {
          Matcher coordinate = coordinatePattern.matcher(declaration.group(2));
          if (coordinate.find()
              && declaration.group(2).matches("(?s).*\\b(?:platform|enforcedPlatform)\\s*\\(.*")) {
            String value = resolveVersion(coordinate.group(1), variables);
            if (value.startsWith("org.springframework.boot:spring-boot-dependencies:")
                && pinnedGradle(value.substring(value.lastIndexOf(':') + 1)))
              managed.add(declaration.group(1));
          }
        }
      }
      for (String body : blocks) {
        Matcher declaration = declarationPattern.matcher(body);
        while (declaration.find()) {
          Matcher coordinate = coordinatePattern.matcher(declaration.group(2));
          if (!coordinate.find()) {
            Matcher local =
                Pattern.compile(
                        "^\\s*\\(?\\s*files\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)\\s*\\)?\\s*$")
                    .matcher(declaration.group(2));
            assertTrue(
                local.find(),
                "Cannot statically resolve Gradle declaration: " + declaration.group());
            Path target = file.getParent().resolve(local.group(1)).normalize();
            assertTrue(
                target.startsWith(ROOT) && Files.exists(target), "Missing local Gradle dependency");
            continue;
          }
          String value = resolveVersion(coordinate.group(1), variables);
          String[] parts = value.split(":", -1);
          assertTrue(parts.length >= 2, "Invalid dependency coordinate: " + value);
          if (parts.length == 2)
            assertTrue(
                (boot || managed.contains(declaration.group(1)))
                    && parts[0].equals("org.springframework.boot")
                    && parts[1].startsWith("spring-boot-starter"),
                "No pinned version or verified BOM for " + value);
          else
            assertTrue(
                pinnedGradle(parts[2]), "Unpinned or unresolved Gradle dependency: " + value);
        }
      }
    }
  }

  private static final class Command implements AutoCloseable {
    final Process process;
    final long started = System.nanoTime();
    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    final Thread reader;

    Command(String script, Path directory) throws Exception {
      ProcessBuilder builder =
          new ProcessBuilder("bash", "-e", "-o", "pipefail", "-c", script)
              .directory(directory.toFile())
              .redirectErrorStream(true);
      builder.environment().put("PROJECT_CHECK_ACTIVE", "1");
      builder.environment().remove("NODE_TEST_CONTEXT");
      process = builder.start();
      reader =
          new Thread(
              () -> {
                try (InputStream input = process.getInputStream()) {
                  byte[] bytes = new byte[4096];
                  for (int count; (count = input.read(bytes)) >= 0; )
                    synchronized (output) {
                      if (output.size() < 1048576)
                        output.write(bytes, 0, Math.min(count, 1048576 - output.size()));
                    }
                } catch (Exception ignored) {
                }
              });
      reader.setDaemon(true);
      reader.start();
    }

    String text() {
      synchronized (output) {
        return output.toString(StandardCharsets.UTF_8);
      }
    }

    double seconds() {
      return (System.nanoTime() - started) / 1e9;
    }

    void await(double seconds, Command app) throws Exception {
      long deadline = System.nanoTime() + (long) (seconds * 1e9);
      while (!process.waitFor(20, TimeUnit.MILLISECONDS)) {
        assertTrue(System.nanoTime() < deadline, "Command timed out: " + text());
        assertTrue(app == null || app.process.isAlive(), "Run command exited during tests");
      }
      reader.join(1000);
      assertEquals(0, process.exitValue(), text());
    }

    @Override
    public void close() throws Exception {
      List<ProcessHandle> descendants = process.toHandle().descendants().toList();
      descendants.forEach(ProcessHandle::destroy);
      process.destroy();
      process.waitFor(200, TimeUnit.MILLISECONDS);
      descendants.forEach(
          child -> {
            if (child.isAlive()) child.destroyForcibly();
          });
      if (process.isAlive()) process.destroyForcibly();
      process.waitFor(3, TimeUnit.SECONDS);
      reader.join(1000);
    }
  }

  private static String quote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private static String tool(String name) {
    if (name.equals("eslint"))
      for (String parent : List.of("", "frontend", "backend")) {
        Path file = ROOT.resolve(parent).resolve("node_modules/.bin").resolve(name);
        if (Files.isExecutable(file)) return file.toString();
      }
    return name;
  }

  @Test
  void javascriptLint() throws Exception {
    List<String> targets =
        files(ROOT, true).stream()
            .filter(file -> file.getFileName().toString().matches(".*\\.[cm]?jsx?$"))
            .map(file -> "./" + relative(ROOT, file))
            .toList();
    if (targets.isEmpty()) return;
    List<String> args =
        new ArrayList<>(
            List.of(
                tool("eslint"),
                "--no-config-lookup",
                "--no-inline-config",
                "--no-ignore",
                "--max-warnings",
                "0",
                "--config",
                resource("eslint.config.mjs").toString(),
                "--"));
    args.addAll(targets);
    try (Command command =
        new Command(String.join(" ", args.stream().map(ProjectChecksTest::quote).toList()), ROOT)) {
      command.await(timeout("LINT", 120), null);
    }
  }

  @Test
  void javaLint() throws Exception {
    List<String> targets =
        files(ROOT, true).stream()
            .filter(file -> file.toString().endsWith(".java"))
            .map(file -> "./" + relative(ROOT, file))
            .toList();
    if (targets.isEmpty()) return;
    List<String> args =
        new ArrayList<>(
            List.of("checkstyle", "-c", resource("checkstyle.xml").toString(), "-f", "xml", "--"));
    args.addAll(targets);
    try (Command command =
        new Command(String.join(" ", args.stream().map(ProjectChecksTest::quote).toList()), ROOT)) {
      command.await(timeout("LINT", 120), null);
      Document audit = xml(command.text().getBytes(StandardCharsets.UTF_8));
      assertEquals("checkstyle", audit.getDocumentElement().getTagName());
      assertEquals(0, audit.getElementsByTagName("error").getLength(), command.text());
    }
  }

  private static double timeout(String name, double fallback) {
    double value =
        Double.parseDouble(
            System.getenv()
                .getOrDefault("CHECK_" + name + "_TIMEOUT_SECONDS", Double.toString(fallback)));
    assertTrue(Double.isFinite(value) && value > 0, "Invalid timeout: " + name);
    return value;
  }

  private static boolean matches(String pattern, String name) {
    String[] parts = name.split("/");
    boolean[] before = new boolean[parts.length + 1];
    before[0] = true;
    for (String segment : pattern.split("/")) {
      boolean[] after = new boolean[parts.length + 1];
      if (segment.equals("**")) after[0] = before[0];
      String regex =
          "^" + Pattern.quote(segment).replace("*", "\\E[^/]*\\Q").replace("?", "\\E[^/]\\Q") + "$";
      for (int i = 1; i <= parts.length; i++)
        after[i] =
            segment.equals("**")
                ? before[i] || after[i - 1]
                : before[i - 1] && parts[i - 1].matches(regex);
      before = after;
    }
    return before[parts.length];
  }

  private static Map<String, String> reports(Path directory, List<String> patterns)
      throws Exception {
    Map<String, String> found = new HashMap<>();
    for (Path file : files(directory, false)) {
      String name = relative(directory, file);
      if (name.toLowerCase().endsWith(".xml")
          && patterns.stream().anyMatch(pattern -> matches(pattern, name))) {
        BasicFileAttributes info = Files.readAttributes(file, BasicFileAttributes.class);
        found.put(name, info.fileKey() + ":" + info.size() + ":" + info.lastModifiedTime());
      }
    }
    return found;
  }

  private static void copyProject(Path workspace) throws Exception {
    files(ROOT, true);
    Files.walkFileTree(
        ROOT,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes info)
              throws java.io.IOException {
            String name = relative(ROOT, directory);
            if (EXCLUDED.contains(directory.getFileName().toString()) || GENERATED.contains(name))
              return FileVisitResult.SKIP_SUBTREE;
            Files.createDirectories(workspace.resolve(name));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes info)
              throws java.io.IOException {
            if (!EXCLUDED.contains(file.getFileName().toString()))
              Files.copy(
                  file,
                  workspace.resolve(relative(ROOT, file)),
                  java.nio.file.StandardCopyOption.COPY_ATTRIBUTES);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  @Test
  void installRunTestAndGeneratedXml() throws Exception {
    JsonNode config = configuration(), menu = config.path("ide_config").path("project_menu");
    Path workspace = Files.createTempDirectory("project-check-");
    List<Command> commands = new ArrayList<>();
    try {
      copyProject(workspace);
      Path wrapper = workspace.resolve("backend/gradlew");
      if (Files.isRegularFile(wrapper)) {
        // Resolve the build-tool distribution before measuring application install.
        try (Command setup = new Command("./gradlew --version", wrapper.getParent())) {
          setup.await(timeout("TOOL_SETUP", 600), null);
          System.out.printf("Gradle setup (excluded from install): %.3fs%n", setup.seconds());
        }
      }
      Command install = new Command(menu.path("install").asText(), workspace);
      commands.add(install);
      install.await(timeout("INSTALL", 600), null);
      System.out.printf("Install: %.3fs%n", install.seconds());
      List<String> urls = new ArrayList<>();
      for (JsonNode value :
          JSON.readTree(
              System.getenv()
                  .getOrDefault(
                      "CHECK_READY_URLS",
                      "[\"http://127.0.0.1:3000/\",\"http://127.0.0.1:8000/\"]")))
        urls.add(value.asText());
      assertFalse(urls.isEmpty());
      for (String value : urls) {
        URI url = URI.create(value);
        assertTrue(
            Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(url.getHost()),
            "Readiness URLs must be local");
        boolean occupied = false;
        try (Socket socket = new Socket()) {
          socket.connect(
              new InetSocketAddress(
                  url.getHost(),
                  url.getPort() == -1
                      ? (url.getScheme().equals("https") ? 443 : 80)
                      : url.getPort()),
              250);
          occupied = true;
        } catch (java.io.IOException ignored) {
        }
        assertFalse(occupied, "Readiness port already occupied: " + value);
      }
      Command app = new Command(menu.path("run").asText(), workspace);
      commands.add(app);
      long deadline = System.nanoTime() + (long) (timeout("STARTUP", 120) * 1e9);
      while (true) {
        assertTrue(app.process.isAlive(), "Run command exited: " + app.text());
        boolean ready = true;
        for (String value : urls) {
          HttpURLConnection connection = null;
          try {
            connection = (HttpURLConnection) URI.create(value).toURL().openConnection();
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setInstanceFollowRedirects(false);
            ready &= connection.getResponseCode() < 500;
          } catch (java.io.IOException error) {
            ready = false;
          } finally {
            if (connection != null) connection.disconnect();
          }
        }
        if (ready) break;
        assertTrue(System.nanoTime() < deadline, "Startup timed out: " + app.text());
        Thread.sleep(100);
      }
      System.out.printf("Startup: %.3fs%n", app.seconds());
      String selectedTest = selectedTestCommand(config, workspace);
      if (selectedTest == null) {
        System.out.println("Test and generated XML checks skipped: no test command configured");
        return;
      }
      System.out.printf(
          "Test command source: %s; command: %s%n",
          menu.has("test") ? "hackerrank.yml" : "root package.json scripts.test", selectedTest);
      List<String> patterns = new ArrayList<>();
      for (JsonNode value : config.path("scoring").path("files"))
        if (value.isTextual() && value.asText().toLowerCase().endsWith(".xml"))
          patterns.add(value.asText());
      if (config.path("scoring").has("files"))
        assertFalse(patterns.isEmpty(), "Scoring files must specify XML reports");
      else System.out.println("Generated XML checks skipped: no scoring files configured");
      for (String pattern : patterns)
        assertFalse(
            Path.of(pattern).isAbsolute()
                || Arrays.asList(pattern.split("/")).contains("..")
                || pattern.contains("\\"),
            "Invalid report path: " + pattern);
      Map<String, String> before = reports(workspace, patterns);
      Command test = new Command(selectedTest, workspace);
      commands.add(test);
      List<String> errors = new ArrayList<>();
      try {
        test.await(timeout("TEST", 300), app);
      } catch (AssertionError error) {
        errors.add(error.getMessage());
      }
      System.out.printf("Test: %.3fs%n%s%n", test.seconds(), test.text());
      assertFalse(test.process.isAlive(), "Test command did not finish");
      Map<String, String> after = reports(workspace, patterns);
      for (String pattern : patterns) {
        List<String> names =
            after.keySet().stream().filter(name -> matches(pattern, name)).toList();
        if (names.isEmpty()) errors.add("XML report not generated: " + pattern);
        for (String name : names) {
          if (after.get(name).equals(before.get(name))) errors.add("XML report is stale: " + name);
          try {
            if (!REPORT_ROOTS.contains(
                xml(Files.readAllBytes(workspace.resolve(name)))
                    .getDocumentElement()
                    .getLocalName()
                    .toLowerCase())) errors.add("Not a test report: " + name);
          } catch (Exception error) {
            errors.add("Invalid XML report: " + name + ": " + error.getMessage());
          }
        }
      }
      assertTrue(app.process.isAlive(), "Run command exited during tests");
      assertTrue(errors.isEmpty(), String.join("\n", errors));
    } finally {
      for (int i = commands.size() - 1; i >= 0; i--) commands.get(i).close();
      try (Stream<Path> paths = Files.walk(workspace)) {
        for (Path file : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(file);
      }
    }
  }
}
