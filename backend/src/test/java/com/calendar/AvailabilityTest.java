package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class AvailabilityTest extends ApiTestSupport {

	private Session alexSession;

	private Session jordanSession;

	@BeforeEach
	void signInPeople() throws Exception {
		alexSession = signIn(alex);
		jordanSession = signIn(jordan);
	}

	private ApiResponse conflictsFor(String startAt, String endAt) throws Exception {
		return alexSession.post("/api/v1/availability/conflicts",
				body("participantIds", List.of(jordan.getId()), "startAt", startAt, "endAt", endAt, "timeZone", "UTC"));
	}

	private ApiResponse suggestions(Object... overrides) throws Exception {
		Map<String, Object> request = body("participantIds", List.of(jordan.getId()), "from", "2030-06-03", "timeZone",
				"UTC", "days", 1, "durationMinutes", 60);
		request.putAll(body(overrides));

		return alexSession.post("/api/v1/availability/suggestions", request);
	}

	private static List<List<Object>> busyTitles(ApiResponse response) {
		List<List<Object>> rows = new ArrayList<>();

		for (JsonNode conflict : response.data().path("conflicts")) {
			rows.add(List.of(conflict.path("person").path("name").asText(), texts(conflict.path("busy"), "title")));
		}

		return rows;
	}

	@Test
	@DisplayName("marks a time busy when a guest has their own event or an invitation then, but not a declined one")
	void marksATimeBusyWhenAGuestHasTheirOwnEventOrAnInvitationThenButNotADeclinedOne() throws Exception {
		Session taylorSession = signIn(taylor);
		jordanSession.post("/api/v1/events", eventBody(jordanPrimary, "title", "Dentist"));
		taylorSession.post("/api/v1/events", eventBody(taylorPrimary, "participantIds", List.of(jordan.getId()), "title",
				"Planning", "startAt", "2030-06-03T13:00:00.000Z", "endAt", "2030-06-03T14:00:00.000Z"));
		ApiResponse declined = taylorSession.post("/api/v1/events",
				eventBody(taylorPrimary, "participantIds", List.of(jordan.getId()), "title", "Optional demo", "startAt",
						"2030-06-03T15:00:00.000Z", "endAt", "2030-06-03T16:00:00.000Z"));
		jordanSession.patch("/api/v1/events/" + declined.data().path("_id").asText() + "/response",
				body("status", "declined"));

		ApiResponse ownEvent = conflictsFor("2030-06-03T10:00:00.000Z", "2030-06-03T11:00:00.000Z");
		ApiResponse invited = conflictsFor("2030-06-03T13:00:00.000Z", "2030-06-03T14:00:00.000Z");
		ApiResponse declinedSlot = conflictsFor("2030-06-03T15:00:00.000Z", "2030-06-03T16:00:00.000Z");
		ApiResponse late = conflictsFor("2030-06-03T20:00:00.000Z", "2030-06-03T21:00:00.000Z");

		assertThat(busyTitles(ownEvent)).isEqualTo(List.of(List.of("Jordan Smith", List.of("Dentist"))));
		assertThat(busyTitles(invited)).isEqualTo(List.of(List.of("Jordan Smith", List.of("Planning"))));
		assertThat(declinedSlot.data().path("available").asBoolean(false)).isTrue();
		assertThat(declinedSlot.data().path("withinWorkingHours").asBoolean(false)).isTrue();
		assertThat(late.data().path("available").asBoolean(false)).isTrue();
		assertThat(late.data().path("withinWorkingHours").asBoolean(true)).isFalse();
	}

	@Test
	@DisplayName("suggests times inside everyone's working hours that avoid their events")
	void suggestsTimesInsideEveryonesWorkingHoursThatAvoidTheirEvents() throws Exception {
		jordanSession.post("/api/v1/events", eventBody(jordanPrimary, "title", "Workshop", "startAt",
				"2030-06-03T09:00:00.000Z", "endAt", "2030-06-03T12:00:00.000Z"));

		ApiResponse response = suggestions();
		JsonNode slots = response.data().path("suggestions");

		assertThat(response.status()).isEqualTo(200);
		assertThat(slots.size()).isGreaterThan(0);
		assertThat(slots.path(0).path("startAt").asText()).isEqualTo("2030-06-03T12:00:00.000Z");

		for (JsonNode slot : slots) {
			assertThat(Instant.parse(slot.path("startAt").asText())).isAfterOrEqualTo("2030-06-03T12:00:00Z");
			assertThat(Instant.parse(slot.path("endAt").asText())).isBeforeOrEqualTo("2030-06-03T17:00:00Z");
		}
	}

	@Test
	@DisplayName("checks the rules for a suggestion request")
	void checksTheRulesForASuggestionRequest() throws Exception {
		ApiResponse oddDuration = suggestions("durationMinutes", 20);
		ApiResponse tooManyDays = suggestions("days", 15);
		ApiResponse samePersonTwice = suggestions("participantIds", List.of(jordan.getId(), jordan.getId()));

		for (ApiResponse response : List.of(oddDuration, tooManyDays, samePersonTwice)) {
			assertThat(response.outcome()).isEqualTo(List.of(400, "VALIDATION_ERROR"));
		}
	}

	@Test
	@DisplayName("never suggests a time that has passed, and cuts an event that started earlier to the asked time")
	void neverSuggestsATimeThatHasPassedAndCutsAnEventThatStartedEarlierToTheAskedTime() throws Exception {
		jordanSession.post("/api/v1/events", eventBody(jordanPrimary, "title", "Workshop", "startAt",
				"2030-06-03T09:00:00.000Z", "endAt", "2030-06-03T11:00:00.000Z"));

		ApiResponse past = suggestions("from", "2020-06-01");
		ApiResponse overlapping = conflictsFor("2030-06-03T10:30:00.000Z", "2030-06-03T11:30:00.000Z");
		List<List<List<Object>>> cut = new ArrayList<>();

		for (JsonNode conflict : overlapping.data().path("conflicts")) {
			List<List<Object>> blocks = new ArrayList<>();

			for (JsonNode block : conflict.path("busy")) {
				blocks.add(List.of(block.path("title").asText(), block.path("startAt").asText(),
						block.path("endAt").asText()));
			}

			cut.add(blocks);
		}

		assertThat(past.status()).isEqualTo(200);
		assertThat(past.data().path("suggestions").size()).isEqualTo(0);
		assertThat(overlapping.data().path("available").asBoolean(true)).isFalse();
		assertThat(cut).isEqualTo(
				List.of(List.of(List.of("Workshop", "2030-06-03T10:30:00.000Z", "2030-06-03T11:00:00.000Z"))));
	}
}
