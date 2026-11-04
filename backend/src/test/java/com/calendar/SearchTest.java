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
}
