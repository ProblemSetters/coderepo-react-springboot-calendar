package com.calendar.events;

import java.time.Instant;

public class Occurrence {

	private final Event event;

	private final Instant startAt;

	private final Instant endAt;

	private final boolean recurring;

	private final Instant occurrenceStartAt;

	public Occurrence(Event event) {
		this(event, event.getStartAt(), event.getEndAt(), false, null);
	}

	public Occurrence(Event event, Instant startAt, Instant endAt, boolean recurring, Instant occurrenceStartAt) {
		this.event = event;
		this.startAt = startAt;
		this.endAt = endAt;
		this.recurring = recurring;
		this.occurrenceStartAt = occurrenceStartAt;
	}

	public Event event() {
		return event;
	}

	public Instant startAt() {
		return startAt;
	}

	public Instant endAt() {
		return endAt;
	}

	public boolean recurring() {
		return recurring;
	}

	public Instant occurrenceStartAt() {
		return occurrenceStartAt;
	}
}
