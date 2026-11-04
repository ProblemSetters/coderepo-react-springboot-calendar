package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthTest extends ApiTestSupport {

	@Test
	@DisplayName("signs in with the right password and refuses a wrong one")
	void signsInWithTheRightPasswordAndRefusesAWrongOne() throws Exception {
		ApiResponse signedIn = anonymous().post("/api/v1/auth/login",
				Map.of("email", alex.getEmail(), "password", PASSWORD));
		ApiResponse refused = anonymous().post("/api/v1/auth/login",
				Map.of("email", alex.getEmail(), "password", "not-the-password"));

		assertThat(signedIn.status()).isEqualTo(200);
		assertThat(signedIn.data().path("account").path("email").asText()).isEqualTo(alex.getEmail());
		assertThat(signedIn.data().path("token").isTextual()).isTrue();
		assertThat(refused.status()).isEqualTo(401);
		assertThat(refused.errorCode()).isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	@DisplayName("refuses a request with no token or a bad token")
	void refusesARequestWithNoTokenOrABadToken() throws Exception {
		ApiResponse noToken = anonymous().get("/api/v1/calendars");
		ApiResponse badToken = as("not-a-real-token").get("/api/v1/calendars");

		assertThat(noToken.status()).isEqualTo(401);
		assertThat(noToken.errorCode()).isEqualTo("AUTH_REQUIRED");
		assertThat(badToken.status()).isEqualTo(401);
		assertThat(badToken.errorCode()).isEqualTo("INVALID_TOKEN");
	}

	@Test
	@DisplayName("needs a profile before it reads any calendar data")
	void needsAProfileBeforeItReadsAnyCalendarData() throws Exception {
		String workspaceToken = signInWorkspace(alex);

		ApiResponse response = as(workspaceToken).get("/api/v1/calendars");

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.errorCode()).isEqualTo("PROFILE_REQUIRED");
	}

	@Test
	@DisplayName("switches only to a profile the account may use")
	void switchesOnlyToAProfileTheAccountMayUse() throws Exception {
		String workspaceToken = signInWorkspace(alex);

		ApiResponse profiles = as(workspaceToken).get("/api/v1/profiles");
		ApiResponse own = as(workspaceToken).post("/api/v1/auth/switch-profile", Map.of("profileId", alex.getId()));
		ApiResponse someoneElse = as(workspaceToken).post("/api/v1/auth/switch-profile",
				Map.of("profileId", jordan.getId()));
		ApiResponse calendars = as(own.data().path("token").asText()).get("/api/v1/calendars");

		assertThat(texts(profiles.data(), "name")).isEqualTo(List.of("Alex Morgan"));
		assertThat(own.status()).isEqualTo(200);
		assertThat(calendars.status()).isEqualTo(200);
		assertThat(someoneElse.status()).isEqualTo(403);
		assertThat(someoneElse.errorCode()).isEqualTo("PROFILE_FORBIDDEN");
	}

	@Test
	@DisplayName("returns the signed-in account and signs out")
	void returnsTheSignedInAccountAndSignsOut() throws Exception {
		Session token = signIn(alex);

		ApiResponse session = token.get("/api/v1/auth/session");
		ApiResponse signedOut = token.post("/api/v1/auth/logout");

		assertThat(session.status()).isEqualTo(200);
		assertThat(session.data().path("account").path("email").asText()).isEqualTo(alex.getEmail());
		assertThat(signedOut.status()).isEqualTo(204);
	}
}
