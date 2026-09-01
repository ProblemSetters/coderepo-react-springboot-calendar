package com.calendar.seed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.calendar.auth.WorkspaceAccount;
import com.calendar.calendars.Calendar;
import com.calendar.events.Event;
import com.calendar.people.Person;
import com.calendar.shared.TimeZones;

import at.favre.lib.crypto.bcrypt.BCrypt;

@SpringBootApplication(scanBasePackages = "com.calendar")
@EntityScan("com.calendar")
public class SeedRunner {

	private static final String SEPARATOR = "========================================";

	public static void main(String[] args) {
		var context = new SpringApplicationBuilder(SeedRunner.class).web(WebApplicationType.NONE)
				.logStartupInfo(false).run(args);

		try {
			seed(context.getBean(MongoTemplate.class));
		} catch (RuntimeException exception) {
			System.err.println();
			System.err.println("Seeding failed: " + exception.getMessage());
			exception.printStackTrace();
			SpringApplication.exit(context, () -> 1);
			System.exit(1);
		}

		System.out.println("Disconnected from MongoDB");
		SpringApplication.exit(context, () -> 0);
		System.exit(0);
	}

	static void seed(MongoTemplate mongoTemplate) {
		System.out.println(SEPARATOR);
		System.out.println("Database Seeding");
		System.out.println(SEPARATOR);
		System.out.println();
		System.out.println("Connecting to MongoDB...");
		mongoTemplate.executeCommand(new Document("ping", 1));
		System.out.println("Connected to MongoDB");

		clearDatabase(mongoTemplate);

		String todayKey = todayKey();
		List<Person> profiles = seedProfiles(mongoTemplate);
		seedAccounts(mongoTemplate, profiles);
		Map<String, Calendar> calendars = seedCalendars(mongoTemplate, profiles);
		SeedData data = new SeedData(todayKey, profiles, calendars);
		seedEvents(mongoTemplate, data, calendars);

		report(mongoTemplate, todayKey, profiles);
	}

	private static String todayKey() {
		String pinned = System.getenv("DEMO_TODAY");

		return pinned != null && !pinned.isBlank() ? pinned
				: TimeZones.localDateKey(Instant.now(), SeedData.DEMO_TIME_ZONE);
	}

	private static void clearDatabase(MongoTemplate mongoTemplate) {
		System.out.println("Clearing existing collections...");

		for (Class<?> type : List.of(Event.class, Calendar.class, Person.class, WorkspaceAccount.class)) {
			mongoTemplate.remove(new Query(), type);
		}
	}

	private static List<Person> seedProfiles(MongoTemplate mongoTemplate) {
		System.out.println();
		System.out.println("Seeding profiles...");

		List<Person> profiles = new ArrayList<>();

		for (Person profile : SeedData.profiles()) {
			profile.setId(new ObjectId().toHexString());
			profiles.add(mongoTemplate.insert(profile));
		}

		System.out.println("  Created " + profiles.size() + " profiles");

		return profiles;
	}

	private static void seedAccounts(MongoTemplate mongoTemplate, List<Person> profiles) {
		System.out.println();
		System.out.println("Seeding sign-in accounts...");

		String passwordHash = BCrypt.with(BCrypt.Version.VERSION_2B).hashToString(SeedData.PASSWORD_ROUNDS,
				SeedData.DEMO_PASSWORD.toCharArray());
		List<String> allowedProfileIds = profiles.stream().map(Person::getId).toList();
		int created = 0;

		for (Person profile : profiles) {
			WorkspaceAccount account = new WorkspaceAccount();
			account.setId(new ObjectId().toHexString());
			account.setName(profile.getName());
			account.setEmail(profile.getEmail());
			account.setPasswordHash(passwordHash);
			account.setAllowedProfileIds(new ArrayList<>(allowedProfileIds));
			mongoTemplate.insert(account);
			created += 1;
		}

		System.out.println("  Created " + created + " accounts, each able to open any of the " + profiles.size()
				+ " profiles");
	}

	private static Map<String, Calendar> seedCalendars(MongoTemplate mongoTemplate, List<Person> profiles) {
		System.out.println();
		System.out.println("Seeding calendars...");

		Map<String, String> profileIdByEmail = new LinkedHashMap<>();

		for (Person profile : profiles) {
			profileIdByEmail.put(profile.getEmail(), profile.getId());
		}

		Map<String, Calendar> byKey = new LinkedHashMap<>();

		for (SeedData.CalendarRow row : SeedData.calendarRows()) {
			Calendar calendar = new Calendar();
			calendar.setId(new ObjectId().toHexString());
			calendar.setOwnerId(profileIdByEmail.get(row.ownerEmail()));
			calendar.setName(row.name());
			calendar.setColor(row.color());
			calendar.setDefaultColor(row.color());
			calendar.setPrimary(row.isPrimary());
			byKey.put(row.key(), mongoTemplate.insert(calendar));
		}

		System.out.println("  Created " + byKey.size() + " calendars");

		return byKey;
	}

	private static void seedEvents(MongoTemplate mongoTemplate, SeedData data, Map<String, Calendar> calendars) {
		System.out.println();
		System.out.println("Seeding events...");

		List<Event> rows = data.buildEventRows();
		data.validateEventRows(rows, new ArrayList<>(calendars.values()));

		for (Event row : rows) {
			row.setId(new ObjectId().toHexString());
			mongoTemplate.insert(row);
		}

		System.out.println("  Created " + rows.size() + " events between " + data.dayKey(SeedData.SPREAD_FIRST_DAY)
				+ " and " + data.dayKey(SeedData.SPREAD_LAST_DAY));
	}

	private static void report(MongoTemplate mongoTemplate, String todayKey, List<Person> profiles) {
		long people = mongoTemplate.count(new Query(), Person.class);
		long accounts = mongoTemplate.count(new Query(), WorkspaceAccount.class);
		long calendars = mongoTemplate.count(new Query(), Calendar.class);
		long eventCount = mongoTemplate.count(new Query(), Event.class);

		System.out.println();
		System.out.println(SEPARATOR);
		System.out.println("Seeding completed successfully!");
		System.out.println(SEPARATOR);
		System.out.println();
		System.out.println("Collection counts:");
		System.out.println("  Profiles:  " + people);
		System.out.println("  Accounts:  " + accounts);
		System.out.println("  Calendars: " + calendars);
		System.out.println("  Events:    " + eventCount);
		System.out.println();
		System.out.println("Demo accounts:");

		for (Person profile : profiles) {
			System.out.println("  Email: " + profile.getEmail() + " | Password: " + SeedData.DEMO_PASSWORD);
		}

		System.out.println();
		System.out.println("All times are seeded in " + SeedData.DEMO_TIME_ZONE + ", relative to " + todayKey + ".");
		System.out.println(SEPARATOR);
		System.out.println();
	}
}
