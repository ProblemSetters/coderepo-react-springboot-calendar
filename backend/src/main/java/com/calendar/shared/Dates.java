package com.calendar.shared;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class Dates {

	private static final DateTimeFormatter ISO_MILLISECONDS = DateTimeFormatter
			.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
			.withZone(ZoneOffset.UTC);

	public static String iso(Instant value) {
		return value == null ? null : ISO_MILLISECONDS.format(value);
	}

	private Dates() {
	}
}
