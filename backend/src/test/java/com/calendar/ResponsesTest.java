package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class ResponsesTest extends ApiTestSupport {

	private Session alexSession;

	private Session jordanSession;

	@BeforeEach
	void signInPeople() throws Exception {
		alexSession = signIn(alex);
		jordanSession = signIn(jordan);
	}

	private String inviteJordan(Object... overrides) throws Exception {
		Object[] all = new Object[overrides.length + 2];
		all[0] = "participantIds";
		all[1] = List.of(jordan.getId());
		System.arraycopy(overrides, 0, all, 2, overrides.length);
		ApiResponse response = alexSession.post("/api/v1/events", eventBody(alexWork, all));

		return response.data().path("_id").asText();
	}

	@Test
	@DisplayName("an invited guest answers and the summary counts it, while anyone else is refused")
	void anInvitedGuestAnswersAndTheSummaryCountsItWhileAnyoneElseIsRefused() throws Exception {
		String eventId = inviteJordan();

		ApiResponse answered = jordanSession.patch("/api/v1/events/" + eventId + "/response", body("status", "accepted"));
		ApiResponse organizer = alexSession.patch("/api/v1/events/" + eventId + "/response", body("status", "accepted"));

		assertThat(answered.status()).isEqualTo(200);
		JsonNode summary = answered.data().path("responseSummary");
		assertThat(summary.size()).isEqualTo(4);
		assertThat(summary.path("needsAction").asInt(-1)).isEqualTo(0);
		assertThat(summary.path("accepted").asInt(-1)).isEqualTo(1);
		assertThat(summary.path("declined").asInt(-1)).isEqualTo(0);
		assertThat(summary.path("tentative").asInt(-1)).isEqualTo(0);
		assertThat(organizer.status()).isEqualTo(403);
		assertThat(organizer.errorCode()).isEqualTo("NOT_EVENT_ATTENDEE");
	}

	@Test
	@DisplayName("an answer for one occurrence of a repeat changes only that occurrence")
	void anAnswerForOneOccurrenceOfARepeatChangesOnlyThatOccurrence() throws Exception {
		String eventId = inviteJordan("recurrence", weekly());

		ApiResponse answered = jordanSession.patch("/api/v1/events/" + eventId + "/response",
				body("status", "declined", "scope", "this", "occurrenceStartAt", "2030-06-10T10:00:00.000Z"));
		ApiResponse listed = jordanSession
				.get("/api/v1/events?from=2030-06-01T00:00:00.000Z&to=2030-06-20T00:00:00.000Z");

		assertThat(answered.status()).isEqualTo(200);
		assertThat(pairs(listed.data(), "startAt", "responseStatus"))
				.isEqualTo(List.of(List.of("2030-06-03T10:00:00.000Z", "needsAction"),
						List.of("2030-06-10T10:00:00.000Z", "declined"),
						List.of("2030-06-17T10:00:00.000Z", "needsAction")));
	}

	@Test
	@DisplayName("rejects occurrence choices that do not fit the event")
	void rejectsOccurrenceChoicesThatDoNotFitTheEvent() throws Exception {
		String singleId = inviteJordan();
		String repeatId = inviteJordan("title", "Weekly sync", "recurrence", weekly());

		ApiResponse onSingle = jordanSession.patch("/api/v1/events/" + singleId + "/response",
				body("status", "accepted", "scope", "this", "occurrenceStartAt", "2030-06-03T10:00:00.000Z"));
		ApiResponse noOccurrence = jordanSession.patch("/api/v1/events/" + repeatId + "/response",
				body("status", "accepted", "scope", "this"));
		ApiResponse notInSeries = jordanSession.patch("/api/v1/events/" + repeatId + "/response",
				body("status", "accepted", "scope", "this", "occurrenceStartAt", "2030-06-11T10:00:00.000Z"));

		assertThat(onSingle.outcome()).isEqualTo(List.of(400, "NOT_RECURRING"));
		assertThat(noOccurrence.outcome()).isEqualTo(List.of(400, "OCCURRENCE_REQUIRED"));
		assertThat(notInSeries.outcome()).isEqualTo(List.of(400, "INVALID_OCCURRENCE"));
	}
}
