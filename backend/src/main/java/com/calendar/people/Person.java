package com.calendar.people;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import com.calendar.shared.Dates;

@Document(collection = "people")
public class Person {

	@Id
	private String id;

	private String name;

	private String email;

	private String avatarColor;

	private boolean isProfile;

	private String headline = "";

	private int sortOrder;

	private String timeZone = "UTC";

	private WorkingHours workingHours = new WorkingHours();

	private List<BusyBlock> busyBlocks = new ArrayList<>();

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	public Map<String, Object> toMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("_id", id);
		body.put("name", name);
		body.put("email", email);
		body.put("avatarColor", avatarColor);
		body.put("isProfile", isProfile);
		body.put("headline", headline);
		body.put("sortOrder", sortOrder);
		body.put("timeZone", timeZone);
		body.put("workingHours", workingHours.toMap());
		body.put("createdAt", Dates.iso(createdAt));
		body.put("updatedAt", Dates.iso(updatedAt));

		return body;
	}

	public Map<String, Object> toSummaryMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("_id", id);
		body.put("name", name);
		body.put("email", email);
		body.put("avatarColor", avatarColor);

		return body;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getAvatarColor() {
		return avatarColor;
	}

	public void setAvatarColor(String avatarColor) {
		this.avatarColor = avatarColor;
	}

	public boolean isProfile() {
		return isProfile;
	}

	public void setProfile(boolean isProfile) {
		this.isProfile = isProfile;
	}

	public String getHeadline() {
		return headline;
	}

	public void setHeadline(String headline) {
		this.headline = headline;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public void setTimeZone(String timeZone) {
		this.timeZone = timeZone;
	}

	public WorkingHours getWorkingHours() {
		return workingHours;
	}

	public void setWorkingHours(WorkingHours workingHours) {
		this.workingHours = workingHours;
	}

	public List<BusyBlock> getBusyBlocks() {
		return busyBlocks;
	}

	public void setBusyBlocks(List<BusyBlock> busyBlocks) {
		this.busyBlocks = busyBlocks;
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
