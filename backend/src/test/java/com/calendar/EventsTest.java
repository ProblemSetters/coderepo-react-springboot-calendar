package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.calendar.people.Person;
import com.fasterxml.jackson.databind.JsonNode;

class EventsTest extends ApiTestSupport {

	private Session alexSession;

	private Session jordanSession;

	@BeforeEach
	void signInPeople() throws Exception {
		alexSession = signIn(alex);
		jordanSession = signIn(jordan);
	}

	private Object[] invitingJordan(Object... overrides) {
		Object[] all = new Object[overrides.length + 2];
		all[0] = "participantIds";
		all[1] = List.of(jordan.getId());
		System.arraycopy(overrides, 0, all, 2, overrides.length);

		return all;
	}

	@Test
	@DisplayName("creates an event in my calendar with its guests")
	void createsAnEventInMyCalendarWithItsGuests() throws Exception {
		ApiResponse response = alexSession.post("/api/v1/events", eventBody(alexWork, invitingJordan()));

		assertThat(response.status()).isEqualTo(201);
		JsonNode event = response.data();
		assertThat(event.path("title").asText()).isEqualTo("Design review");
		assertThat(event.path("organizerPerson").path("name").asText()).isEqualTo("Alex Morgan");
		assertThat(event.path("participantPeople").size()).isEqualTo(1);
		assertThat(event.path("participantPeople").path(0).path("name").asText()).isEqualTo("Jordan Smith");
		assertThat(event.path("participantPeople").path(0).path("responseStatus").asText()).isEqualTo("needsAction");
		assertThat(event.path("responseSummary").path("needsAction").asInt()).isEqualTo(1);
		assertThat(event.path("responseSummary").path("accepted").asInt()).isEqualTo(0);
		assertThat(event.path("responseSummary").path("declined").asInt()).isEqualTo(0);
		assertThat(event.path("responseSummary").path("tentative").asInt()).isEqualTo(0);
	}

	@Test
	@DisplayName("refuses an event that ends before it starts, a repeat that ends before it starts, and a bad body")
	void refusesAnEventThatEndsBeforeItStartsARepeatThatEndsBeforeItStartsAndABadBody() throws Exception {
		ApiResponse backwards = alexSession.post("/api/v1/events",
				eventBody(alexWork, "endAt", "2030-06-03T09:00:00.000Z"));
		ApiResponse repeatEndsEarly = alexSession.post("/api/v1/events", eventBody(alexWork, "recurrence",
				weekly("endType", "until", "until", "2030-06-01T00:00:00.000Z")));
		ApiResponse noTitle = alexSession.post("/api/v1/events", eventBody(alexWork, "title", ""));

		assertThat(backwards.outcome()).isEqualTo(List.of(400, "INVALID_EVENT_RANGE"));
		assertThat(repeatEndsEarly.outcome()).isEqualTo(List.of(400, "INVALID_RECURRENCE_END"));
		assertThat(noTitle.outcome()).isEqualTo(List.of(400, "VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("refuses to add an event to a calendar I do not own")
	void refusesToAddAnEventToACalendarIDoNotOwn() throws Exception {
		ApiResponse response = alexSession.post("/api/v1/events", eventBody(jordanPrimary));

		assertThat(response.status()).isEqualTo(422);
		assertThat(response.errorCode()).isEqualTo("INVALID_CALENDAR");
	}

	@Test
	@DisplayName("lists single events and every occurrence of a weekly repeat inside the range")
	void listsSingleEventsAndEveryOccurrenceOfAWeeklyRepeatInsideTheRange() throws Exception {
		alexSession.post("/api/v1/events", eventBody(alexWork, "title", "Launch", "startAt", "2030-06-05T15:00:00.000Z",
				"endAt", "2030-06-05T16:00:00.000Z"));
		alexSession.post("/api/v1/events", eventBody(alexWork, "title", "Standup", "recurrence", weekly()));
		alexSession.post("/api/v1/events", eventBody(alexWork, "title", "Retro", "startAt", "2030-07-01T10:00:00.000Z",
				"endAt", "2030-07-01T11:00:00.000Z"));

		ApiResponse response = alexSession.get("/api/v1/events?from=2030-06-01T00:00:00.000Z&to=2030-06-15T00:00:00.000Z"
				+ "&calendarIds=" + alexWork.getId());

		assertThat(response.status()).isEqualTo(200);
		assertThat(pairs(response.data(), "title", "startAt")).isEqualTo(
				List.of(List.of("Standup", "2030-06-03T10:00:00.000Z"), List.of("Launch", "2030-06-05T15:00:00.000Z"),
						List.of("Standup", "2030-06-10T10:00:00.000Z")));
	}

	@Test
	@DisplayName("lets an invited guest open an event but not change it, and hides it from everyone else")
	void letsAnInvitedGuestOpenAnEventButNotChangeItAndHidesItFromEveryoneElse() throws Exception {
		ApiResponse created = alexSession.post("/api/v1/events", eventBody(alexWork, invitingJordan()));
		String path = "/api/v1/events/" + created.data().path("_id").asText();
		Session taylorSession = signIn(taylor);

		ApiResponse guestOpens = jordanSession.get(path);
		ApiResponse guestEdits = jordanSession.patch(path, body("title", "Moved by a guest"));
		ApiResponse guestDeletes = jordanSession.delete(path);
		ApiResponse strangerOpens = taylorSession.get(path);

		assertThat(guestOpens.status()).isEqualTo(200);
		assertThat(guestOpens.data().path("editable").asBoolean(true)).isFalse();
		assertThat(guestEdits.status()).isEqualTo(404);
		assertThat(guestDeletes.status()).isEqualTo(404);
		assertThat(strangerOpens.status()).isEqualTo(404);
		assertThat(strangerOpens.errorCode()).isEqualTo("EVENT_NOT_FOUND");
	}

	@Test
	@DisplayName("resets every guest's answer when the time moves, but keeps it when only the title changes")
	void resetsEveryGuestsAnswerWhenTheTimeMovesButKeepsItWhenOnlyTheTitleChanges() throws Exception {
		ApiResponse created = alexSession.post("/api/v1/events", eventBody(alexWork, invitingJordan()));
		String path = "/api/v1/events/" + created.data().path("_id").asText();
		jordanSession.patch(path + "/response", body("status", "accepted"));

		alexSession.patch(path, body("title", "Design review (room change)"));
		ApiResponse afterRename = jordanSession.get(path);
		alexSession.patch(path, body("startAt", "2030-06-03T14:00:00.000Z", "endAt", "2030-06-03T15:00:00.000Z"));
		ApiResponse afterMove = jordanSession.get(path);

		assertThat(afterRename.data().path("responseStatus").asText()).isEqualTo("accepted");
		assertThat(afterMove.data().path("responseStatus").asText()).isEqualTo("needsAction");
	}

	// The editor always sends the guest names and their ids together, so the test does the same.
	private static Map<String, Object> guests(Person... people) {
		return body("participants", Arrays.stream(people).map(Person::getName).toList(), "participantIds",
				Arrays.stream(people).map(Person::getId).toList());
	}

	@Test
	@DisplayName("keeps answers when a guest is added, shuts out a removed guest, and resets answers when the repeat rule changes")
	void keepsAnswersWhenAGuestIsAddedShutsOutARemovedGuestAndResetsAnswersWhenTheRepeatRuleChanges() throws Exception {
		Session taylorSession = signIn(taylor);
		ApiResponse created = alexSession.post("/api/v1/events",
				eventBody(alexWork, invitingJordan("recurrence", weekly())));
		String path = "/api/v1/events/" + created.data().path("_id").asText();
		jordanSession.patch(path + "/response", body("status", "accepted"));

		ApiResponse added = alexSession.patch(path, guests(jordan, taylor));
		taylorSession.patch(path + "/response", body("status", "accepted"));
		ApiResponse removed = alexSession.patch(path, guests(taylor));
		ApiResponse removedGuestOpens = jordanSession.get(path);
		alexSession.patch(path, body("recurrence", weekly("daysOfWeek", List.of(1, 3))));
		ApiResponse afterRuleChange = taylorSession.get(path);

		assertThat(pairs(added.data().path("participantPeople"), "name", "responseStatus"))
				.isEqualTo(List.of(List.of("Jordan Smith", "accepted"), List.of("Taylor Johnson", "needsAction")));
		assertThat(pairs(removed.data().path("participantPeople"), "name", "responseStatus"))
				.isEqualTo(List.of(List.of("Taylor Johnson", "accepted")));
		assertThat(removedGuestOpens.status()).isEqualTo(404);
		assertThat(afterRuleChange.data().path("responseStatus").asText()).isEqualTo("needsAction");
	}

	@Test
	@DisplayName("refuses a guest id that is malformed, unknown or listed twice")
	void refusesAGuestIdThatIsMalformedUnknownOrListedTwice() throws Exception {
		ApiResponse malformed = alexSession.post("/api/v1/events",
				eventBody(alexWork, "participantIds", List.of("not-an-id")));
		ApiResponse unknown = alexSession.post("/api/v1/events",
				eventBody(alexWork, "participantIds", List.of("0123456789abcdef01234567")));
		ApiResponse twice = alexSession.post("/api/v1/events",
				eventBody(alexWork, "participantIds", List.of(jordan.getId(), jordan.getId())));

		assertThat(malformed.outcome()).isEqualTo(List.of(400, "VALIDATION_ERROR"));
		assertThat(unknown.outcome()).isEqualTo(List.of(404, "PEOPLE_NOT_FOUND"));
		assertThat(twice.outcome()).isEqualTo(List.of(400, "VALIDATION_ERROR"));
	}

	@Test
	@DisplayName("deletes an event and treats a malformed id as not found")
	void deletesAnEventAndTreatsAMalformedIdAsNotFound() throws Exception {
		ApiResponse created = alexSession.post("/api/v1/events", eventBody(alexWork));
		String path = "/api/v1/events/" + created.data().path("_id").asText();

		ApiResponse deleted = alexSession.delete(path);
		ApiResponse afterDelete = alexSession.get(path);
		ApiResponse malformed = alexSession.get("/api/v1/events/not-an-id");

		assertThat(deleted.status()).isEqualTo(204);
		assertThat(afterDelete.status()).isEqualTo(404);
		assertThat(malformed.outcome()).isEqualTo(List.of(404, "EVENT_NOT_FOUND"));
	}
}
