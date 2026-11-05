package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

class InsightsTest extends ApiTestSupport {

	private static final String DAY = "from=2030-06-03T00:00:00.000Z&to=2030-06-04T00:00:00.000Z";

	private Session alexSession;

	@BeforeEach
	void signInAlex() throws Exception {
		alexSession = signIn(alex);
	}

	private void add(Object... overrides) throws Exception {
		alexSession.post("/api/v1/events", eventBody(alexWork, overrides));
	}

	private static Map<String, Integer> minutesBy(JsonNode insight) {
		Map<String, Integer> minutes = new LinkedHashMap<>();

		for (JsonNode category : insight.path("categories")) {
			minutes.put(category.path("key").asText(), category.path("minutes").asInt());
		}

		return minutes;
	}

	@Test
	@DisplayName("adds up the day's meetings, focus time and tasks against an eight-hour day")
	void addsUpTheDaysMeetingsFocusTimeAndTasksAgainstAnEightHourDay() throws Exception {
		add("title", "Design review", "type", "event", "startAt", "2030-06-03T10:00:00.000Z", "endAt",
				"2030-06-03T11:00:00.000Z");
		add("title", "Deep work", "type", "focusTime", "startAt", "2030-06-03T13:00:00.000Z", "endAt",
				"2030-06-03T15:00:00.000Z");
		add("title", "Send notes", "type", "task", "startAt", "2030-06-03T16:00:00.000Z", "endAt",
				"2030-06-03T16:30:00.000Z");

		ApiResponse response = alexSession.get("/api/v1/insights/daily?" + DAY);
		JsonNode insight = response.data();

		assertThat(response.status()).isEqualTo(200);
		assertThat(insight.path("meetingMinutes").asInt(-1)).isEqualTo(60);
		assertThat(insight.path("meetingCount").asInt(-1)).isEqualTo(1);
		assertThat(insight.path("totalScheduledMinutes").asInt(-1)).isEqualTo(210);
		assertThat(insight.path("remainingMinutes").asInt(-1)).isEqualTo(270);
		assertThat(minutesBy(insight))
				.isEqualTo(Map.of("meetings", 60, "focus", 120, "tasks", 30, "outOfOffice", 0));
	}

	@Test
	@DisplayName("counts an all-day out of office as a full working day and other all-day events as nothing")
	void countsAnAllDayOutOfOfficeAsAFullWorkingDayAndOtherAllDayEventsAsNothing() throws Exception {
		add("title", "Holiday", "type", "outOfOffice", "allDay", true, "startAt", "2030-06-03T00:00:00.000Z", "endAt",
				"2030-06-04T00:00:00.000Z");
		add("title", "Conference", "type", "event", "allDay", true, "startAt", "2030-06-03T00:00:00.000Z", "endAt",
				"2030-06-04T00:00:00.000Z");

		JsonNode insight = alexSession.get("/api/v1/insights/daily?" + DAY).data();

		assertThat(minutesBy(insight)).isEqualTo(Map.of("meetings", 0, "focus", 0, "tasks", 0, "outOfOffice", 480));
		assertThat(insight.path("remainingMinutes").asInt(-1)).isEqualTo(0);
	}
}
