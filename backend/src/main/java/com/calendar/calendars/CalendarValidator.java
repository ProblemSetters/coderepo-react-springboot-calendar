package com.calendar.calendars;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.calendar.shared.FieldErrors;
import com.calendar.shared.Messages;
import com.calendar.shared.Requests;
import com.calendar.shared.TimeZones;

public final class CalendarValidator {

	private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

	private static final int NAME_MAX = 80;

	private static final int DESCRIPTION_MAX = 1000;

	private static final int TIME_ZONE_MAX = 100;

	private static final String COLOR_MESSAGE = "Invalid string: must match pattern /^#[0-9A-Fa-f]{6}$/";

	private static final String TIME_ZONE_MESSAGE = "Provide a valid IANA time zone.";

	public record CreateInput(String name, String color, String description, String timeZone) {
	}

	public static CreateInput validateCreate(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		String name = readName(input, errors);
		String color = readColor(input, errors, true);
		String description = readDescription(input, errors, "");
		String timeZone = readTimeZone(input, errors, "UTC");

		errors.throwIfAny();

		return new CreateInput(name, color, description, timeZone);
	}

	public static Map<String, Object> validateUpdate(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		Map<String, Object> updates = new LinkedHashMap<>();

		if (input.containsKey("name")) {
			updates.put("name", readName(input, errors));
		}

		if (input.containsKey("color")) {
			updates.put("color", readColor(input, errors, false));
		}

		if (input.containsKey("description")) {
			updates.put("description", readDescription(input, errors, null));
		}

		if (input.containsKey("timeZone")) {
			updates.put("timeZone", readTimeZone(input, errors, null));
		}

		if (input.containsKey("visible")) {
			Boolean visible = Requests.bool(input, "visible");

			if (visible == null) {
				errors.add("visible", Messages.expected("boolean", "undefined"));
			} else {
				updates.put("visible", visible);
			}
		}

		if (updates.isEmpty() && errors.isEmpty()) {
			errors.addFormError("Provide at least one field to update.");
		}

		errors.throwIfAny();

		return updates;
	}

	private static String readName(Map<String, Object> input, FieldErrors errors) {
		String name = Requests.string(input, "name");

		if (name == null) {
			errors.add("name", Messages.MISSING_STRING);

			return null;
		}

		String trimmed = name.trim();

		if (trimmed.isEmpty()) {
			errors.add("name", Requests.tooSmallString(1));
		} else if (trimmed.length() > NAME_MAX) {
			errors.add("name", Requests.tooBigString(NAME_MAX));
		}

		return trimmed;
	}

	private static String readColor(Map<String, Object> input, FieldErrors errors, boolean required) {
		String color = Requests.string(input, "color");

		if (color == null) {
			if (required) {
				errors.add("color", Messages.MISSING_STRING);
			}

			return null;
		}

		if (!COLOR.matcher(color).matches()) {
			errors.add("color", COLOR_MESSAGE);
		}

		return color;
	}

	private static String readDescription(Map<String, Object> input, FieldErrors errors, String fallback) {
		String description = Requests.string(input, "description");

		if (description == null) {
			if (fallback == null) {
				errors.add("description", Messages.MISSING_STRING);
			}

			return fallback;
		}

		String trimmed = description.trim();

		if (trimmed.length() > DESCRIPTION_MAX) {
			errors.add("description", Requests.tooBigString(DESCRIPTION_MAX));
		}

		return trimmed;
	}

	private static String readTimeZone(Map<String, Object> input, FieldErrors errors, String fallback) {
		String timeZone = Requests.string(input, "timeZone");

		if (timeZone == null) {
			if (fallback == null) {
				errors.add("timeZone", Messages.MISSING_STRING);
			}

			return fallback;
		}

		String trimmed = timeZone.trim();

		if (trimmed.isEmpty()) {
			errors.add("timeZone", Requests.tooSmallString(1));
		} else if (trimmed.length() > TIME_ZONE_MAX) {
			errors.add("timeZone", Requests.tooBigString(TIME_ZONE_MAX));
		} else if (!TimeZones.isTimeZone(trimmed)) {
			errors.add("timeZone", TIME_ZONE_MESSAGE);
		}

		return trimmed;
	}

	private CalendarValidator() {
	}
}
