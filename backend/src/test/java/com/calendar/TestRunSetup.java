package com.calendar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;

// Runs once per test run, before Spring starts. Every test run gets its own database, <db>_test_<pid>,
// dropped when the run ends, so two runs never share data. A database left by a run that was killed is
// dropped at the start of the next run.
public class TestRunSetup implements LauncherSessionListener {

	private static final String DEFAULT_URI = "mongodb://localhost:27017/calendar_db";

	private static final String DEFAULT_DATABASE = "calendar_db";

	private static final String URI_KEY = "MONGODB_URI";

	private static final String URI_PROPERTY = "spring.data.mongodb.uri";

	private static final String RUN_SUFFIX = "_test_";

	private static final Pattern URI_PARTS = Pattern.compile("^(mongodb(?:\\+srv)?://[^/?]*)/?([^?]*)(\\?.*)?$");

	private static final long SERVER_SELECTION_TIMEOUT_SECONDS = 5;

	private final long pid = ProcessHandle.current().pid();

	private String appDatabase;

	private String runDatabase;

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		String appUri = mongoUri();
		Matcher parts = URI_PARTS.matcher(appUri);

		if (!parts.matches()) {
			System.err.println("Could not read the MongoDB address " + appUri + ", so the tests use the app's database.");

			return;
		}

		appDatabase = parts.group(2).isEmpty() ? DEFAULT_DATABASE : parts.group(2);
		runDatabase = appDatabase + RUN_SUFFIX + pid;
		String query = parts.group(3) == null ? "" : parts.group(3);
		System.setProperty(URI_PROPERTY, parts.group(1) + "/" + runDatabase + query);
		withClient(appUri, client -> {
			for (String name : client.listDatabaseNames()) {
				if (isDeadRunDatabase(name)) {
					client.getDatabase(name).drop();
				}
			}
		});
	}

	@Override
	public void launcherSessionClosed(LauncherSession session) {
		if (runDatabase != null && runDatabase.contains(RUN_SUFFIX)) {
			withClient(mongoUri(), client -> client.getDatabase(runDatabase).drop());
		}
	}

	private boolean isDeadRunDatabase(String name) {
		String prefix = appDatabase + RUN_SUFFIX;
		String runPid = name.startsWith(prefix) ? name.substring(prefix.length()) : "";

		return runPid.matches("\\d+") && ProcessHandle.of(Long.parseLong(runPid)).isEmpty();
	}

	private static void withClient(String uri, Consumer<MongoClient> action) {
		MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(new ConnectionString(uri))
				.applyToClusterSettings(
						cluster -> cluster.serverSelectionTimeout(SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
				.build();

		try (MongoClient client = MongoClients.create(settings)) {
			action.accept(client);
		} catch (RuntimeException exception) {
			System.err.println("Could not set up the test databases: " + exception.getMessage());
		}
	}

	private static String mongoUri() {
		return Optional.ofNullable(System.getenv(URI_KEY)).or(TestRunSetup::uriFromEnvFile).orElse(DEFAULT_URI);
	}

	private static Optional<String> uriFromEnvFile() {
		try {
			return Files.readAllLines(Path.of(".env")).stream().map(String::strip)
					.filter(line -> line.startsWith(URI_KEY + "=")).map(line -> line.substring(URI_KEY.length() + 1).strip())
					.map(value -> value.replaceAll("^[\"']|[\"']$", "")).reduce((first, last) -> last);
		} catch (IOException exception) {
			return Optional.empty();
		}
	}
}
