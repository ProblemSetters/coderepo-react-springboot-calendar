package com.calendar.events;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

@Document(collection = "events")
public class Event {

	@Id
	private String id;

	@Field(targetType = FieldType.OBJECT_ID)
	private String calendarId;

	private String title;

	private String type = "event";

	private String description = "";

	private String location = "";

	private String organizer = "Calendar owner";

	private List<String> participants = new ArrayList<>();

	@Field(targetType = FieldType.OBJECT_ID)
	private List<String> participantIds = new ArrayList<>();

	private List<AttendeeResponse> attendeeResponses = new ArrayList<>();

	private Recurrence recurrence = new Recurrence();

	private List<RecurrenceResponseOverride> recurrenceResponseOverrides = new ArrayList<>();

	private Instant startAt;

	private Instant endAt;

	private boolean allDay;

	private String color;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getCalendarId() {
		return calendarId;
	}

	public void setCalendarId(String calendarId) {
		this.calendarId = calendarId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public String getOrganizer() {
		return organizer;
	}

	public void setOrganizer(String organizer) {
		this.organizer = organizer;
	}

	public List<String> getParticipants() {
		return participants;
	}

	public void setParticipants(List<String> participants) {
		this.participants = participants;
	}

	public List<String> getParticipantIds() {
		return participantIds;
	}

	public void setParticipantIds(List<String> participantIds) {
		this.participantIds = participantIds;
	}

	public List<AttendeeResponse> getAttendeeResponses() {
		return attendeeResponses;
	}

	public void setAttendeeResponses(List<AttendeeResponse> attendeeResponses) {
		this.attendeeResponses = attendeeResponses;
	}

	public Recurrence getRecurrence() {
		return recurrence;
	}

	public void setRecurrence(Recurrence recurrence) {
		this.recurrence = recurrence;
	}

	public List<RecurrenceResponseOverride> getRecurrenceResponseOverrides() {
		return recurrenceResponseOverrides;
	}

	public void setRecurrenceResponseOverrides(List<RecurrenceResponseOverride> recurrenceResponseOverrides) {
		this.recurrenceResponseOverrides = recurrenceResponseOverrides;
	}

	public Instant getStartAt() {
		return startAt;
	}

	public void setStartAt(Instant startAt) {
		this.startAt = startAt;
	}

	public Instant getEndAt() {
		return endAt;
	}

	public void setEndAt(Instant endAt) {
		this.endAt = endAt;
	}

	public boolean isAllDay() {
		return allDay;
	}

	public void setAllDay(boolean allDay) {
		this.allDay = allDay;
	}

	public String getColor() {
		return color;
	}

	public void setColor(String color) {
		this.color = color;
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
