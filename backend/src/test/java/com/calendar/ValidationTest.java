package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ValidationTest extends ApiTestSupport {

	private Map<String, Object> repeating(Object... overrides) {
		return eventBody(alexWork, "recurrence", weekly(overrides));
	}

	@Test
	@DisplayName("refuses bad colours, time zones and repeat settings, empty updates and over-long ranges, and saves nothing")
	void refusesBadColoursTimeZonesAndRepeatSettingsEmptyUpdatesAndOverLongRangesAndSavesNothing() throws Exception {
		Session alexSession = signIn(alex);
		ApiResponse saved = alexSession.post("/api/v1/events", eventBody(alexWork));
		String eventPath = "/api/v1/events/" + saved.data().path("_id").asText();

		Map<String, ApiResponse> attempts = new LinkedHashMap<>();
		attempts.put("eventColour", alexSession.post("/api/v1/events", eventBody(alexWork, "color", "#12345")));
		attempts.put("weeklyWithoutDays",
				alexSession.post("/api/v1/events", repeating("daysOfWeek", List.of())));
		attempts.put("countWithoutNumber", alexSession.post("/api/v1/events", repeating("endType", "count")));
		attempts.put("untilWithoutDate", alexSession.post("/api/v1/events", repeating("endType", "until")));
		attempts.put("repeatTimeZone",
				alexSession.post("/api/v1/events", repeating("timeZone", "Mars/Olympus")));
		attempts.put("emptyEventUpdate", alexSession.patch(eventPath, body()));
		attempts.put("rangeOverLimit", alexSession.get("/api/v1/events?from=2030-01-01T00:00:00.000Z"
				+ "&to=2031-01-10T00:00:00.000Z&calendarIds=" + alexWork.getId()));
		attempts.put("calendarColour",
				alexSession.post("/api/v1/calendars", body("name", "Side projects", "color", "green")));
		attempts.put("calendarTimeZone", alexSession.post("/api/v1/calendars",
				body("name", "Side projects", "color", "#0b8043", "timeZone", "Mars/Olympus")));
		attempts.put("emptyCalendarUpdate",
				alexSession.patch("/api/v1/calendars/" + alexWork.getId(), body()));
		ApiResponse events = alexSession.get("/api/v1/events?from=2030-06-01T00:00:00.000Z&to=2030-07-01T00:00:00.000Z"
				+ "&calendarIds=" + alexWork.getId());
		ApiResponse myCalendars = alexSession.get("/api/v1/calendars");

		Map<String, List<Object>> outcome = new LinkedHashMap<>();
		Map<String, List<Object>> refused = new LinkedHashMap<>();

		attempts.forEach((name, response) -> {
			outcome.put(name, response.outcome());
			refused.put(name, List.of(400, "VALIDATION_ERROR"));
		});

		assertThat(outcome).isEqualTo(refused);
		assertThat(texts(events.data(), "title")).isEqualTo(List.of("Design review"));
		assertThat(texts(myCalendars.data(), "name")).isEqualTo(List.of("Alex Morgan", "Work"));
	}
}
