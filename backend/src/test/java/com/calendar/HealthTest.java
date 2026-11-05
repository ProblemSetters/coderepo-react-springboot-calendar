package com.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HealthTest extends ApiTestSupport {

	@Test
	@DisplayName("reports the database as connected")
	void reportsTheDatabaseAsConnected() throws Exception {
		ApiResponse response = anonymous().get("/api/v1/health");

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.data().path("status").asText()).isEqualTo("ok");
		assertThat(response.data().path("database").asText()).isEqualTo("connected");
		assertThat(response.data().size()).isEqualTo(2);
	}
}
