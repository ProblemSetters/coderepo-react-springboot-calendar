package com.calendar.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "workspaceaccounts")
public class WorkspaceAccount {

	@Id
	private String id;

	private String name;

	private String email;

	private String passwordHash;

	@Field(targetType = FieldType.OBJECT_ID)
	private List<String> allowedProfileIds = new ArrayList<>();

	private boolean active = true;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	public Map<String, Object> toPublicMap() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("_id", id);
		body.put("name", name);
		body.put("email", email);

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

	public String getPasswordHash() {
		return passwordHash;
	}

	public void setPasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public List<String> getAllowedProfileIds() {
		return allowedProfileIds;
	}

	public void setAllowedProfileIds(List<String> allowedProfileIds) {
		this.allowedProfileIds = allowedProfileIds;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
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
