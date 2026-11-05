package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchTest extends ApiTestSupport {

	private Session alexSession;

	@BeforeEach
	void signInAlex() throws Exception {
		alexSession = signIn(alex);
	}

	@Test
	@DisplayName("finds my events by title and never shows events I cannot see")
	void findsMyEventsByTitleAndNeverShowsEventsICannotSee() throws Exception {
		Session jordanSession = signIn(jordan);
		alexSession.post("/api/v1/events", eventBody(alexWork, "title", "Quarterly planning"));
		alexSession.post("/api/v1/events", eventBody(alexWork, "title", "Hiring sync"));
		jordanSession.post("/api/v1/events", eventBody(jordanPrimary, "title", "Quarterly budget"));

		ApiResponse response = alexSession.get(
				"/api/v1/events/search?q=quarterly&calendarIds=" + alexPrimary.getId() + "," + alexWork.getId());

		assertThat(response.status()).isEqualTo(200);
		assertThat(texts(response.data(), "title")).isEqualTo(List.of("Quarterly planning"));
	}

	@Test
	@DisplayName("finds people by name or email, leaving out the person searching")
	void findsPeopleByNameOrEmailLeavingOutThePersonSearching() throws Exception {
		ApiResponse byName = alexSession.get("/api/v1/people?q=jor");
		ApiResponse byEmail = alexSession.get("/api/v1/people?q=taylor@calendar");
		ApiResponse self = alexSession.get("/api/v1/people?q=alex");

		assertThat(texts(byName.data(), "name")).isEqualTo(List.of("Jordan Smith"));
		assertThat(texts(byEmail.data(), "name")).isEqualTo(List.of("Taylor Johnson"));
		assertThat(self.data().isArray()).isTrue();
		assertThat(self.data().size()).isEqualTo(0);
	}

	private void roadmap(String title, String location, String day) throws Exception {
		alexSession.post("/api/v1/events", eventBody(alexWork, "title", title, "location", location, "startAt",
				"2030-06-" + day + "T10:00:00.000Z", "endAt", "2030-06-" + day + "T10:30:00.000Z"));
	}

	@Test
	@DisplayName("narrows a search by place and dates, leaves out excluded words, and finds a repeat inside the dates")
	void narrowsASearchByPlaceAndDatesLeavesOutExcludedWordsAndFindsARepeatInsideTheDates() throws Exception {
		roadmap("Roadmap review", "Room 4", "04");
		roadmap("Roadmap review draft", "Room 4", "05");
		roadmap("Roadmap review", "Online", "06");
		roadmap("Roadmap review", "Room 4", "20");
		alexSession.post("/api/v1/events", eventBody(alexWork, "title", "Roadmap sync", "location", "Room 4", "startAt",
				"2030-05-06T09:00:00.000Z", "endAt", "2030-05-06T09:30:00.000Z", "recurrence",
				weekly("daysOfWeek", List.of(1))));

		ApiResponse response = alexSession.get("/api/v1/events/search?q=roadmap&where=room%204&exclude=draft"
				+ "&from=2030-06-01T00:00:00.000Z&to=2030-06-08T00:00:00.000Z&calendarIds=" + alexPrimary.getId() + ","
				+ alexWork.getId());

		assertThat(response.status()).isEqualTo(200);
		assertThat(pairs(response.data(), "title", "startAt")).containsExactlyInAnyOrder(
				List.of("Roadmap review", "2030-06-04T10:00:00.000Z"),
				List.of("Roadmap sync", "2030-06-03T09:00:00.000Z"));
	}
}
