package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import com.calendar.calendars.Calendar;

class CalendarsTest extends ApiTestSupport {

	private Session alexSession;

	@BeforeEach
	void signInAlex() throws Exception {
		alexSession = signIn(alex);
	}

	@Test
	@DisplayName("lists only the signed-in profile's calendars, primary first")
	void listsOnlyTheSignedInProfilesCalendarsPrimaryFirst() throws Exception {
		ApiResponse response = alexSession.get("/api/v1/calendars");

		assertThat(response.status()).isEqualTo(200);
		assertThat(pairs(response.data(), "name", "isPrimary"))
				.isEqualTo(List.of(List.of("Alex Morgan", true), List.of("Work", false)));
	}

	@Test
	@DisplayName("creates, renames and recolours a calendar, and refuses a name already in use")
	void createsRenamesAndRecoloursACalendarAndRefusesANameAlreadyInUse() throws Exception {
		ApiResponse created = alexSession.post("/api/v1/calendars", body("name", "Projects", "color", "#f4511e"));
		ApiResponse renamed = alexSession.patch("/api/v1/calendars/" + created.data().path("_id").asText(),
				body("name", "Roadmap", "color", "#039be5"));
		ApiResponse duplicate = alexSession.post("/api/v1/calendars", body("name", "work", "color", "#f4511e"));

		assertThat(created.status()).isEqualTo(201);
		assertThat(renamed.status()).isEqualTo(200);
		assertThat(renamed.data().path("name").asText()).isEqualTo("Roadmap");
		assertThat(renamed.data().path("color").asText()).isEqualTo("#039be5");
		assertThat(duplicate.status()).isEqualTo(409);
		assertThat(duplicate.errorCode()).isEqualTo("CALENDAR_NAME_CONFLICT");
	}

	@Test
	@DisplayName("keeps one calendar when the same name is created twice at once")
	void keepsOneCalendarWhenTheSameNameIsCreatedTwiceAtOnce() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);

		try {
			List<Future<ApiResponse>> pending = List.of(pool.submit(() -> createLaunch(ready, go)),
					pool.submit(() -> createLaunch(ready, go)));
			ready.await();
			go.countDown();
			List<Integer> statuses = List.of(pending.get(0).get().status(), pending.get(1).get().status()).stream()
					.sorted().toList();

			assertThat(statuses).isEqualTo(List.of(201, 409));
		} finally {
			pool.shutdownNow();
		}

		Query launches = Query.query(Criteria.where("ownerId").is(new ObjectId(alex.getId())).and("name").is("Launch"));

		assertThat(mongoTemplate.count(launches, Calendar.class)).isEqualTo(1);
	}

	private ApiResponse createLaunch(CountDownLatch ready, CountDownLatch go) throws Exception {
		ready.countDown();
		go.await();

		return alexSession.post("/api/v1/calendars", body("name", "Launch", "color", "#f4511e"));
	}

	@Test
	@DisplayName("shows only the chosen calendar when asked to display it alone")
	void showsOnlyTheChosenCalendarWhenAskedToDisplayItAlone() throws Exception {
		ApiResponse response = alexSession.post("/api/v1/calendars/" + alexWork.getId() + "/display-only");

		assertThat(response.status()).isEqualTo(200);
		assertThat(pairs(response.data(), "name", "visible"))
				.isEqualTo(List.of(List.of("Alex Morgan", false), List.of("Work", true)));
	}

	@Test
	@DisplayName("never deletes the primary calendar or one that still has events")
	void neverDeletesThePrimaryCalendarOrOneThatStillHasEvents() throws Exception {
		ApiResponse primary = alexSession.delete("/api/v1/calendars/" + alexPrimary.getId());
		ApiResponse event = alexSession.post("/api/v1/events", eventBody(alexWork));
		ApiResponse withEvents = alexSession.delete("/api/v1/calendars/" + alexWork.getId());
		alexSession.delete("/api/v1/events/" + event.data().path("_id").asText());
		ApiResponse empty = alexSession.delete("/api/v1/calendars/" + alexWork.getId());

		assertThat(primary.status()).isEqualTo(409);
		assertThat(primary.errorCode()).isEqualTo("PRIMARY_CALENDAR");
		assertThat(withEvents.status()).isEqualTo(409);
		assertThat(withEvents.errorCode()).isEqualTo("CALENDAR_NOT_EMPTY");
		assertThat(empty.status()).isEqualTo(204);
	}

	@Test
	@DisplayName("keeps my calendars away from other profiles")
	void keepsMyCalendarsAwayFromOtherProfiles() throws Exception {
		Session jordanSession = signIn(jordan);

		ApiResponse listed = jordanSession.get("/api/v1/calendars");
		ApiResponse renamed = jordanSession.patch("/api/v1/calendars/" + alexWork.getId(), body("name", "Mine now"));
		ApiResponse deleted = jordanSession.delete("/api/v1/calendars/" + alexWork.getId());

		assertThat(texts(listed.data(), "name")).isEqualTo(List.of("Jordan Smith"));
		assertThat(renamed.status()).isEqualTo(404);
		assertThat(deleted.status()).isEqualTo(404);
		assertThat(deleted.errorCode()).isEqualTo("CALENDAR_NOT_FOUND");
	}
}
