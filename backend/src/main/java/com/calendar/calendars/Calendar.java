package com.calendar.calendars;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import com.calendar.shared.Dates;

@Document(collection = "calendars")
public class Calendar {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String ownerId;

	private String name;

	private String color;

	private String defaultColor;

	private String description = "";

	private String timeZone = "UTC";

	private boolean visible = true;

	private boolean isPrimary;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	public Map<String, Object> toMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("_id", id);
		body.put("ownerId", ownerId);
		body.put("name", name);
		body.put("color", color);
		body.put("defaultColor", defaultColor);
		body.put("description", description);
		body.put("timeZone", timeZone);
		body.put("visible", visible);
		body.put("isPrimary", isPrimary);
		body.put("createdAt", Dates.iso(createdAt));
		body.put("updatedAt", Dates.iso(updatedAt));

		return body;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
	}

	public String getDefaultColor() {
		return defaultColor;
	}

	public void setDefaultColor(String defaultColor) {
		this.defaultColor = defaultColor;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}

	public boolean isPrimary() {
		return isPrimary;
	}

	public void setPrimary(boolean isPrimary) {
		this.isPrimary = isPrimary;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}
}
