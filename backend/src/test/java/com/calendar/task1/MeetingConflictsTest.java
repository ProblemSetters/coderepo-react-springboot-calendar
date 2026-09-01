package com.calendar.task1;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.calendar.auth.WorkspaceAccount;
import com.calendar.calendars.Calendar;
import com.calendar.events.AttendeeResponse;
import com.calendar.events.Event;
import com.calendar.events.Recurrence;
import com.calendar.people.BusyBlock;
import com.calendar.people.Person;
import com.calendar.support.ApiTestBase;

import at.favre.lib.crypto.bcrypt.BCrypt;

class MeetingConflictsTest extends ApiTestBase {

	private static final String ORGANIZER_EMAIL = "alex.morgan@calendar.com";

	private static final String DEMO_PASSWORD = "password123";

	private static final int TEST_PASSWORD_ROUNDS = 4;

	private static final String MONDAY = "2030-01-07";

	private static final String TUESDAY = "2030-01-08";

	private Person organizer;

	private Person guestOne;

	private Person guestTwo;

	private Person guestThree;

	private Person bystander;

	private Calendar organizerCalendar;

	private Calendar guestCalendar;

	private String organizerToken;

	@BeforeEach
	void signInAsOrganizer() {
		organizer = person("Alex Morgan", ORGANIZER_EMAIL, "#039be5", 1);
		guestOne = person("Jordan Smith", "jordan.smith@calendar.com", "#e37400", 2);
		guestTwo = person("Taylor Johnson", "taylor.johnson@calendar.com", "#d93025", 3);
		guestThree = person("Riley Parker", "riley.parker@calendar.com", "#7e57c2", 4);
		bystander = person("Casey Bennett", "casey.bennett@calendar.com", "#0f9d58", 5);
		organizerCalendar = calendar(organizer, "#039be5");
		guestCalendar = calendar(guestOne, "#e37400");

		WorkspaceAccount account = new WorkspaceAccount();
		account.setName(organizer.getName());
		account.setEmail(ORGANIZER_EMAIL);
		account.setPasswordHash(BCrypt.with(BCrypt.Version.VERSION_2B).hashToString(TEST_PASSWORD_ROUNDS,
				DEMO_PASSWORD.toCharArray()));
		account.setAllowedProfileIds(List.of(organizer.getId(), guestOne.getId(), guestTwo.getId(),
				guestThree.getId(), bystander.getId()));
		mongoTemplate.insert(account);

		organizerToken = signIn(ORGANIZER_EMAIL, DEMO_PASSWORD, organizer.getId());
	}

	@Test
	@DisplayName("reports a clash only when the proposed time genuinely overlaps a busy block")
	void reportsGenuineOverlapsOnly() {
		guestOne.setBusyBlocks(List.of(busyBlock("Design sync", at(MONDAY, "10:00"), at(MONDAY, "11:00"))));
		mongoTemplate.save(guestOne);

		ApiResponse backToBack = checkConflicts(List.of(guestOne), at(MONDAY, "11:00"), at(MONDAY, "11:30"));
		assertThat(backToBack.status()).isEqualTo(200);
		assertThat(data(backToBack).get("available")).isEqualTo(true);
		assertThat(rows(data(backToBack), "conflicts")).isEmpty();

		ApiResponse surrounding = checkConflicts(List.of(guestOne), at(MONDAY, "09:00"), at(MONDAY, "12:00"));
		assertThat(surrounding.status()).isEqualTo(200);
		assertThat(data(surrounding).get("available")).isEqualTo(false);
		assertThat(busyIds(surrounding)).containsExactly(guestOne.getId());
	}

	@Test
	@DisplayName("treats invitations that were not declined as busy time for that guest only")
	void treatsUndeclinedInvitationsAsBusy() {
		Event event = event(organizerCalendar, organizer, "Release readiness", "event", at(MONDAY, "14:00"),
				at(MONDAY, "15:00"));
		event.setParticipants(List.of(guestOne.getName(), guestTwo.getName(), guestThree.getName()));
		event.setParticipantIds(List.of(guestOne.getId(), guestTwo.getId(), guestThree.getId()));
		event.setAttendeeResponses(List.of(new AttendeeResponse(guestOne.getId(), "needsAction", null),
				new AttendeeResponse(guestTwo.getId(), "tentative", at(MONDAY, "09:00")),
				new AttendeeResponse(guestThree.getId(), "declined", at(MONDAY, "09:00"))));
		mongoTemplate.insert(event);

		ApiResponse response = checkConflicts(List.of(guestOne, guestTwo, guestThree, bystander),
				at(MONDAY, "14:15"), at(MONDAY, "14:45"));
		assertThat(response.status()).isEqualTo(200);
		assertThat(data(response).get("available")).isEqualTo(false);
		assertThat(busyIds(response)).contains(guestOne.getId(), guestTwo.getId());
		assertThat(busyIds(response)).doesNotContain(guestThree.getId(), bystander.getId());
	}

	@Test
	@DisplayName("treats an all-day out-of-office as busy while other all-day items stay free")
	void treatsOnlyOutOfOfficeAsBusy() {
		mongoTemplate.insert(allDay("Out of office", "outOfOffice"));
		mongoTemplate.insert(allDay("Home", "workingLocation"));
		mongoTemplate.insert(allDay("Company offsite", "event"));

		ApiResponse response = checkConflicts(List.of(guestOne), at(MONDAY, "11:00"), at(MONDAY, "11:30"));
		assertThat(response.status()).isEqualTo(200);
		assertThat(data(response).get("available")).isEqualTo(false);
		assertThat(busyBlocks(response)).hasSize(1);
		assertThat(busyBlocks(response).get(0).get("type")).isEqualTo("outOfOffice");
	}

	@Test
	@DisplayName("reports every occurrence of a recurring meeting as busy")
	void reportsEveryRecurringOccurrence() {
		Event event = event(guestCalendar, guestOne, "Weekly planning", "event", at(MONDAY, "10:00"),
				at(MONDAY, "10:30"));
		event.setRecurrence(weekly(5));
		mongoTemplate.insert(event);

		ApiResponse thirdOccurrence = checkConflicts(List.of(guestOne), at("2030-01-21", "10:00"),
				at("2030-01-21", "10:30"));
		assertThat(thirdOccurrence.status()).isEqualTo(200);
		assertThat(data(thirdOccurrence).get("available")).isEqualTo(false);
		assertThat(busyBlocks(thirdOccurrence)).hasSize(1);

		ApiResponse dayAfter = checkConflicts(List.of(guestOne), at("2030-01-22", "10:00"),
				at("2030-01-22", "10:30"));
		assertThat(dayAfter.status()).isEqualTo(200);
		assertThat(data(dayAfter).get("available")).isEqualTo(true);
	}

	@Test
	@DisplayName("clips each reported busy block to the proposed meeting window")
	void clipsBusyBlocksToTheWindow() {
		mongoTemplate.insert(allDay("Out of office", "outOfOffice"));

		ApiResponse response = checkConflicts(List.of(guestOne), at(MONDAY, "11:00"), at(MONDAY, "11:30"));
		assertThat(response.status()).isEqualTo(200);

		Map<String, Object> block = busyBlocks(response).get(0);
		assertThat(block.get("startAt")).isEqualTo(iso(MONDAY, "11:00"));
		assertThat(block.get("endAt")).isEqualTo(iso(MONDAY, "11:30"));
	}

	@Test
	@DisplayName("never suggests a time when the organizer or a guest is already busy")
	void neverSuggestsBusyTimes() {
		Event metrics = event(organizerCalendar, organizer, "Metrics review", "event", at("2029-12-17", "10:00"),
				at("2029-12-17", "11:00"));
		metrics.setRecurrence(weekly(8));
		mongoTemplate.insert(metrics);
		mongoTemplate.insert(event(guestCalendar, guestOne, "Focus time", "event", at(MONDAY, "09:00"),
				at(MONDAY, "09:30")));

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("participantIds", List.of(guestOne.getId()));
		request.put("from", MONDAY);
		request.put("timeZone", "UTC");
		request.put("days", 1);
		request.put("durationMinutes", 30);

		ApiResponse response = post("/api/v1/availability/suggestions", request, organizerToken);
		assertThat(response.status()).isEqualTo(200);

		Map<String, Object> owner = owner(response);
		assertThat(rows(owner, "busy")).hasSize(1);

		List<Object> starts = new ArrayList<>();
		rows(data(response), "suggestions").forEach(suggestion -> starts.add(suggestion.get("startAt")));
		assertThat(starts).contains(iso(MONDAY, "11:30"));
		assertThat(starts).doesNotContain(iso(MONDAY, "10:00"), iso(MONDAY, "10:30"), iso(MONDAY, "09:00"));
	}

	private ApiResponse checkConflicts(List<Person> people, Instant startAt, Instant endAt) {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("participantIds", people.stream().map(Person::getId).toList());
		request.put("startAt", startAt.toString());
		request.put("endAt", endAt.toString());
		request.put("timeZone", "UTC");

		return post("/api/v1/availability/conflicts", request, organizerToken);
	}

	private List<String> busyIds(ApiResponse response) {
		return rows(data(response), "conflicts").stream()
				.map(conflict -> String.valueOf(person(conflict).get("_id"))).toList();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> person(Map<String, Object> conflict) {
		return (Map<String, Object>) conflict.get("person");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> owner(ApiResponse response) {
		return (Map<String, Object>) data(response).get("owner");
	}

	private List<Map<String, Object>> busyBlocks(ApiResponse response) {
		return rows(rows(data(response), "conflicts").get(0), "busy");
	}

	private Person person(String name, String email, String avatarColor, int sortOrder) {
		Person person = new Person();
		person.setName(name);
		person.setEmail(email);
		person.setAvatarColor(avatarColor);
		person.setProfile(true);
		person.setSortOrder(sortOrder);

		return mongoTemplate.insert(person);
	}

	private Calendar calendar(Person owner, String color) {
		Calendar calendar = new Calendar();
		calendar.setOwnerId(owner.getId());
		calendar.setName("My calendar");
		calendar.setColor(color);
		calendar.setDefaultColor(color);
		calendar.setVisible(true);
		calendar.setPrimary(true);

		return mongoTemplate.insert(calendar);
	}

	private Event event(Calendar calendar, Person owner, String title, String type, Instant startAt, Instant endAt) {
		Event event = new Event();
		event.setCalendarId(calendar.getId());
		event.setOrganizer(owner.getName());
		event.setTitle(title);
		event.setType(type);
		event.setAllDay(false);
		event.setStartAt(startAt);
		event.setEndAt(endAt);

		return event;
	}

	private Event allDay(String title, String type) {
		Event event = event(guestCalendar, guestOne, title, type, at(MONDAY, "00:00"), at(TUESDAY, "00:00"));
		event.setAllDay(true);

		return event;
	}

	private Recurrence weekly(int count) {
		Recurrence recurrence = new Recurrence();
		recurrence.setFrequency("weekly");
		recurrence.setInterval(1);
		recurrence.setEndType("count");
		recurrence.setCount(count);
		recurrence.setTimeZone("UTC");

		return recurrence;
	}

	private BusyBlock busyBlock(String title, Instant startAt, Instant endAt) {
		BusyBlock block = new BusyBlock();
		block.setTitle(title);
		block.setStartAt(startAt);
		block.setEndAt(endAt);

		return block;
	}

	private Instant at(String date, String time) {
		return Instant.parse(iso(date, time));
	}

	private String iso(String date, String time) {
		return date + "T" + time + ":00.000Z";
	}
}
