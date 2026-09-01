package com.calendar.events;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import com.calendar.shared.Dates;

public class RecurrenceResponseOverride {

	@Field(targetType = FieldType.OBJECT_ID)
	private String personId;

	private Instant occurrenceStartAt;

	private String scope;

	private String status;

	private Instant respondedAt;

	public RecurrenceResponseOverride() {
	}

	public RecurrenceResponseOverride(String personId, Instant occurrenceStartAt, String scope, String status,
			Instant respondedAt) {
		this.personId = personId;
		this.occurrenceStartAt = occurrenceStartAt;
		this.scope = scope;
		this.status = status;
		this.respondedAt = respondedAt;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("personId", personId);
		body.put("occurrenceStartAt", Dates.iso(occurrenceStartAt));
		body.put("scope", scope);
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

	public Instant getOccurrenceStartAt() {
		return occurrenceStartAt;
	}

	public void setOccurrenceStartAt(Instant occurrenceStartAt) {
		this.occurrenceStartAt = occurrenceStartAt;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
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
