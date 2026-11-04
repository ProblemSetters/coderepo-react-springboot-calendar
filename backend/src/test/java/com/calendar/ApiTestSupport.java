package com.calendar;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.calendar.auth.WorkspaceAccount;
import com.calendar.calendars.Calendar;
import com.calendar.events.Event;
import com.calendar.people.Person;
import com.calendar.people.WorkingHours;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

import at.favre.lib.crypto.bcrypt.BCrypt;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

	protected static final String PASSWORD = "Calendar#2026";

	// The app hashes with its own cost. Tests only need a valid hash, so they use the cheapest one.
	private static final int TEST_HASH_ROUNDS = 4;

	private static final int WORKING_START_MINUTE = 9 * 60;

	private static final int WORKING_END_MINUTE = 17 * 60;

	private static final DateTimeFormatter MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
			.withZone(ZoneOffset.UTC);

	protected record ApiResponse(int status, JsonNode body) {

		public JsonNode data() {
			return body.path("data");
		}

		public String errorCode() {
			JsonNode code = body.path("error").path("code");

			return code.isTextual() ? code.asText() : null;
		}

		// The status and the error code together, the way a Node test compares them.
		public List<Object> outcome() {
			return Arrays.<Object>asList(status, errorCode());
		}
	}

	// Requests made with one bearer token (or none).
	protected final class Session {

		private final String token;

		private Session(String token) {
			this.token = token;
		}

		public ApiResponse get(String path) throws Exception {
			return send(MockMvcRequestBuilders.get(URI.create(path)));
		}

		public ApiResponse post(String path) throws Exception {
			return post(path, Map.of());
		}

		public ApiResponse post(String path, Object body) throws Exception {
			return send(MockMvcRequestBuilders.post(URI.create(path)).contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(body)));
		}

		public ApiResponse patch(String path, Object body) throws Exception {
			return send(MockMvcRequestBuilders.patch(URI.create(path)).contentType(MediaType.APPLICATION_JSON)
					.content(objectMapper.writeValueAsString(body)));
		}

		public ApiResponse delete(String path) throws Exception {
			return send(MockMvcRequestBuilders.delete(URI.create(path)));
		}

		private ApiResponse send(MockHttpServletRequestBuilder request) throws Exception {
			if (token != null) {
				request.header("Authorization", "Bearer " + token);
			}

			var result = mockMvc.perform(request).andReturn().getResponse();
			String content = result.getContentAsString(StandardCharsets.UTF_8);
			JsonNode body = content.isBlank() ? MissingNode.getInstance() : objectMapper.readTree(content);

			return new ApiResponse(result.getStatus(), body);
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	protected MongoTemplate mongoTemplate;

	protected Person alex;

	protected Person jordan;

	protected Person taylor;

	protected Calendar alexPrimary;

	protected Calendar alexWork;

	protected Calendar jordanPrimary;

	protected Calendar taylorPrimary;

	// Empties every collection, then builds the three-person workspace. The app creates its own indexes at
	// startup, and emptying a collection keeps them, so the tests run against the indexes the app declares.
	@BeforeEach
	void resetWorkspace() {
		for (Class<?> type : List.of(WorkspaceAccount.class, Person.class, Calendar.class, Event.class)) {
			mongoTemplate.remove(new Query(), type);
		}

		alex = createProfile("Alex Morgan", "alex@calendar.test", 1);
		jordan = createProfile("Jordan Smith", "jordan@calendar.test", 2);
		taylor = createProfile("Taylor Johnson", "taylor@calendar.test", 3);

		for (Person profile : List.of(alex, jordan, taylor)) {
			createAccount(profile);
		}

		alexPrimary = createCalendar(alex, "Alex Morgan", "#1a73e8", true);
		alexWork = createCalendar(alex, "Work", "#0b8043", false);
		jordanPrimary = createCalendar(jordan, "Jordan Smith", "#8e24aa", true);
		taylorPrimary = createCalendar(taylor, "Taylor Johnson", "#e67c73", true);
	}

	private Person createProfile(String name, String email, int sortOrder) {
		Person person = new Person();
		person.setName(name);
		person.setEmail(email);
		person.setAvatarColor("#1a73e8");
		person.setProfile(true);
		person.setSortOrder(sortOrder);
		person.setTimeZone("UTC");
		person.setWorkingHours(new WorkingHours(WORKING_START_MINUTE, WORKING_END_MINUTE));

		return mongoTemplate.insert(person);
	}

	// One account per person, allowed to use only that person's own profile.
	private void createAccount(Person profile) {
		WorkspaceAccount account = new WorkspaceAccount();
		account.setName(profile.getName());
		account.setEmail(profile.getEmail());
		account.setPasswordHash(
				BCrypt.with(BCrypt.Version.VERSION_2B).hashToString(TEST_HASH_ROUNDS, PASSWORD.toCharArray()));
		account.setAllowedProfileIds(new ArrayList<>(List.of(profile.getId())));
		mongoTemplate.insert(account);
	}

	private Calendar createCalendar(Person owner, String name, String color, boolean primary) {
		Calendar calendar = new Calendar();
		calendar.setOwnerId(owner.getId());
		calendar.setName(name);
		calendar.setColor(color);
		calendar.setDefaultColor(color);
		calendar.setPrimary(primary);

		return mongoTemplate.insert(calendar);
	}

	protected Session as(String token) {
		return new Session(token);
	}

	protected Session anonymous() {
		return new Session(null);
	}

	protected String signInWorkspace(Person person) throws Exception {
		ApiResponse response = anonymous().post("/api/v1/auth/login",
				Map.of("email", person.getEmail(), "password", PASSWORD));

		return response.data().path("token").asText();
	}

	// Signs in and picks the person's own profile, like the sign-in screen does.
	protected Session signIn(Person person) throws Exception {
		ApiResponse response = as(signInWorkspace(person)).post("/api/v1/auth/switch-profile",
				Map.of("profileId", person.getId()));

		return as(response.data().path("token").asText());
	}

	// A plain map in the order given, for request bodies: body("title", "Standup", "allDay", true).
	protected static Map<String, Object> body(Object... pairs) {
		Map<String, Object> map = new LinkedHashMap<>();

		for (int index = 0; index < pairs.length; index += 2) {
			map.put((String) pairs[index], pairs[index + 1]);
		}

		return map;
	}

	protected static Map<String, Object> eventBody(Calendar calendar, Object... overrides) {
		Map<String, Object> map = body("calendarId", calendar.getId(), "title", "Design review", "startAt",
				"2030-06-03T10:00:00.000Z", "endAt", "2030-06-03T11:00:00.000Z");
		map.putAll(body(overrides));

		return map;
	}

	// A weekly repeat on Mondays that never ends, with any of its fields replaced.
	protected static Map<String, Object> weekly(Object... overrides) {
		Map<String, Object> recurrence = body("frequency", "weekly", "interval", 1, "daysOfWeek", List.of(1), "endType",
				"never", "timeZone", "UTC");
		recurrence.putAll(body(overrides));

		return recurrence;
	}

	// A response timestamp in the same form the Node tests compare against: toISOString().
	protected static String iso(JsonNode node) {
		return MILLIS.format(Instant.parse(node.asText()));
	}

	// One [first, second] pair per element, so a list reads like the Node test's array of arrays.
	protected static List<List<Object>> pairs(JsonNode array, String firstKey, String secondKey) {
		List<List<Object>> rows = new ArrayList<>();

		for (JsonNode element : array) {
			rows.add(Arrays.<Object>asList(plain(element.path(firstKey), firstKey), plain(element.path(secondKey), secondKey)));
		}

		return rows;
	}

	protected static List<String> texts(JsonNode array, String key) {
		List<String> values = new ArrayList<>();

		for (JsonNode element : array) {
			values.add(element.path(key).asText());
		}

		return values;
	}

	private static Object plain(JsonNode node, String key) {
		if (key.endsWith("At")) {
			return iso(node);
		}

		return node.isBoolean() ? (Object) node.asBoolean() : node.isNumber() ? (Object) node.asInt() : node.asText();
	}
}
