package com.calendar.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.calendar.auth.WorkspaceAccount;
import com.calendar.calendars.Calendar;
import com.calendar.events.Event;
import com.calendar.people.Person;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestBase {

	@LocalServerPort
	protected int port;

	@Autowired
	protected TestRestTemplate rest;

	@Autowired
	protected MongoTemplate mongoTemplate;

	public record ApiResponse(int status, Map<String, Object> body) {
	}

	@BeforeEach
	void resetDatabase() {
		for (Class<?> type : List.of(Calendar.class, Event.class, Person.class, WorkspaceAccount.class)) {
			mongoTemplate.remove(new Query(), type);
		}
	}

	@SuppressWarnings("unchecked")
	protected ApiResponse post(String path, Object body, String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		if (token != null) {
			headers.setBearerAuth(token);
		}

		ResponseEntity<Map> response = rest.exchange("http://localhost:" + port + path, HttpMethod.POST,
				new HttpEntity<>(body, headers), Map.class);

		return new ApiResponse(response.getStatusCode().value(),
				response.getBody() == null ? Map.of() : response.getBody());
	}

	protected String signIn(String email, String password, String profileId) {
		Map<String, Object> credentials = new LinkedHashMap<>();
		credentials.put("email", email);
		credentials.put("password", password);

		ApiResponse login = post("/api/v1/auth/login", credentials, null);
		ApiResponse switched = post("/api/v1/auth/switch-profile", Map.of("profileId", profileId),
				(String) data(login).get("token"));

		return (String) data(switched).get("token");
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> data(ApiResponse response) {
		return (Map<String, Object>) response.body().get("data");
	}

	@SuppressWarnings("unchecked")
	protected List<Map<String, Object>> rows(Map<String, Object> body, String key) {
		return (List<Map<String, Object>>) body.get(key);
	}
}
