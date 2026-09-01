package com.calendar.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import com.calendar.shared.Dates;

public class AttendeeResponse {

	@Field(targetType = FieldType.OBJECT_ID)
	private String personId;

	private String status = "needsAction";

	@Field(write = Field.Write.ALWAYS)
	private Instant respondedAt;

	public AttendeeResponse() {
	}

	public AttendeeResponse(String personId, String status, Instant respondedAt) {
		this.personId = personId;
		this.status = status;
		this.respondedAt = respondedAt;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("personId", personId);
		body.put("status", status);
		body.put("respondedAt", Dates.iso(respondedAt));

		return body;
	}

	public String getPersonId() {
		return personId;
	}

	public void setPersonId(String personId) {
		this.personId = personId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Instant getRespondedAt() {
		return respondedAt;
	}

	public void setRespondedAt(Instant respondedAt) {
		this.respondedAt = respondedAt;
	}
}
