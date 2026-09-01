package com.calendar.events;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.calendar.shared.FieldErrors;
import com.calendar.shared.Messages;
import com.calendar.shared.Requests;
import com.calendar.shared.TimeZones;

public final class EventValidator {

	private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-fA-F]{24}$");

	private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

	private static final List<String> TYPES = List.of("event", "task", "outOfOffice", "focusTime", "workingLocation",
			"appointmentSchedule");

	private static final List<String> FREQUENCIES = List.of("none", "daily", "weekly", "monthly", "yearly",
			"weekdays");

	private static final List<String> MONTHLY_MODES = List.of("ordinalWeekday", "dayOfMonth");

	private static final List<String> END_TYPES = List.of("never", "count", "until");

	private static final List<String> RESPONSE_STATUSES = List.of("accepted", "declined", "tentative");

	private static final List<String> RESPONSE_SCOPES = List.of("this", "following", "all");

	private static final int TITLE_MAX = 140;

	private static final int DESCRIPTION_MAX = 2000;

	private static final int LOCATION_MAX = 250;

	private static final int ORGANIZER_MAX = 120;

	private static final int PARTICIPANT_MAX = 100;

	private static final int SEARCH_TEXT_MAX = 100;

	private static final int INTERVAL_MIN = 1;

	private static final int INTERVAL_MAX = 99;

	private static final int COUNT_MIN = 1;

	private static final int COUNT_MAX = 730;

	private static final int RANGE_MAX_DAYS = 370;

	private static final String COLOR_MESSAGE = "Invalid string: must match pattern /^#[0-9A-Fa-f]{6}$/";

	private static final String OBJECT_ID_MESSAGE = "Invalid string: must match pattern /^[0-9a-fA-F]{24}$/";

	private static final String RANGE_MESSAGE = "to must be after from";

	public record ListQuery(Instant from, Instant to, List<String> calendarIds) {
	}

	public record SearchQuery(String what, String who, String where, String exclude, Instant from, Instant to,
			List<String> calendarIds) {
	}

	public record ResponseInput(String status, String scope, Instant occurrenceStartAt) {
	}

	public static ListQuery validateList(Map<String, String> parameters) {
		Map<String, Object> input = Requests.query(parameters);
		FieldErrors errors = new FieldErrors();
		Instant from = readDate(input, "from", errors, true);
		Instant to = readDate(input, "to", errors, true);
		List<String> calendarIds = readCalendarIds(input, errors);

		errors.throwIfAny();

		if (!from.isBefore(to)) {
			errors.add("to", RANGE_MESSAGE);
		} else if (Duration.between(from, to).toDays() > RANGE_MAX_DAYS) {
			errors.add("to", "Event ranges cannot exceed 370 days.");
		}

		errors.throwIfAny();

		return new ListQuery(from, to, calendarIds);
	}

	public static SearchQuery validateSearch(Map<String, String> parameters) {
		Map<String, Object> input = Requests.query(parameters);
		FieldErrors errors = new FieldErrors();
		String query = readSearchText(input, "q", errors);
		String what = readSearchText(input, "what", errors);
		String who = readSearchText(input, "who", errors);
		String where = readSearchText(input, "where", errors);
		String exclude = readSearchText(input, "exclude", errors);
		Instant from = readDate(input, "from", errors, false);
		Instant to = readDate(input, "to", errors, false);
		List<String> calendarIds = readCalendarIds(input, errors);

		errors.throwIfAny();

		if (from != null && to != null && !from.isBefore(to)) {
			errors.add("to", RANGE_MESSAGE);
		}

		errors.throwIfAny();

		String text = what != null ? what : query != null ? query : "";

		return new SearchQuery(text, who, where, exclude, from, to, calendarIds);
	}

	public static Map<String, Object> validateCreate(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		Map<String, Object> values = readFields(input, errors, true);

		errors.throwIfAny();

		return values;
	}

	public static Map<String, Object> validateUpdate(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		Map<String, Object> values = readFields(input, errors, false);

		if (values.isEmpty() && errors.isEmpty()) {
			errors.addFormError("Provide at least one field to update.");
		}

		errors.throwIfAny();

		return values;
	}

	public static ResponseInput validateResponse(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		String status = Requests.string(input, "status");
		String scope = Requests.string(input, "scope");

		if (status == null || !RESPONSE_STATUSES.contains(status)) {
			errors.add("status", Messages.invalidOption(RESPONSE_STATUSES));
		}

		if (input.containsKey("scope") && (scope == null || !RESPONSE_SCOPES.contains(scope))) {
			errors.add("scope", Messages.invalidOption(RESPONSE_SCOPES));
		}

		Instant occurrenceStartAt = null;

		if (input.containsKey("occurrenceStartAt")) {
			occurrenceStartAt = Requests.date(input, "occurrenceStartAt");

			if (occurrenceStartAt == null) {
				errors.add("occurrenceStartAt", Messages.MISSING_DATE);
			}
		}

		errors.throwIfAny();

		return new ResponseInput(status, scope, occurrenceStartAt);
	}

	private static Map<String, Object> readFields(Map<String, Object> input, FieldErrors errors, boolean creating) {
		Map<String, Object> values = new LinkedHashMap<>();

		readString(input, "calendarId", errors, values, creating, null, 1, Integer.MAX_VALUE, false);
		readString(input, "title", errors, values, creating, null, 1, TITLE_MAX, true);
		readEnum(input, "type", errors, values, creating, "event", TYPES);
		readString(input, "description", errors, values, creating, "", 0, DESCRIPTION_MAX, false);
		readString(input, "location", errors, values, creating, "", 0, LOCATION_MAX, false);
		readString(input, "organizer", errors, values, creating, "Calendar owner", 0, ORGANIZER_MAX, true);
		readParticipants(input, errors, values, creating);
		readParticipantIds(input, errors, values, creating);
		readInstant(input, "startAt", errors, values, creating);
		readInstant(input, "endAt", errors, values, creating);
		readBoolean(input, "allDay", errors, values, creating);
		readColor(input, errors, values);
		readRecurrence(input, errors, values);

		return values;
	}

	private static void readString(Map<String, Object> input, String key, FieldErrors errors,
			Map<String, Object> values, boolean creating, String fallback, int minimum, int maximum, boolean trim) {
		if (!input.containsKey(key)) {
			if (creating && fallback == null) {
				errors.add(key, Messages.MISSING_STRING);
			} else if (creating) {
				values.put(key, fallback);
			}

			return;
		}

		String value = Requests.string(input, key);

		if (value == null) {
			errors.add(key, Messages.MISSING_STRING);

			return;
		}

		String result = trim ? value.trim() : value;

		if (result.length() < minimum) {
			errors.add(key, Requests.tooSmallString(minimum));
		} else if (result.length() > maximum) {
			errors.add(key, Requests.tooBigString(maximum));
		}

		values.put(key, result);
	}

	private static void readEnum(Map<String, Object> input, String key, FieldErrors errors, Map<String, Object> values,
			boolean creating, String fallback, List<String> options) {
		if (!input.containsKey(key)) {
			if (creating) {
				values.put(key, fallback);
			}

			return;
		}

		String value = Requests.string(input, key);

		if (value == null || !options.contains(value)) {
			errors.add(key, Messages.invalidOption(options));

			return;
		}

		values.put(key, value);
	}

	private static void readParticipants(Map<String, Object> input, FieldErrors errors, Map<String, Object> values,
			boolean creating) {
		if (!input.containsKey("participants")) {
			if (creating) {
				values.put("participants", new ArrayList<String>());
			}

			return;
		}

		List<Object> items = Requests.list(input, "participants");

		if (items == null) {
			errors.add("participants", Messages.expected("array", "undefined"));

			return;
		}

		if (items.size() > PARTICIPANT_MAX) {
			errors.add("participants", Messages.tooBigArray(PARTICIPANT_MAX));

			return;
		}

		List<String> names = new ArrayList<>();

		for (Object item : items) {
			if (item instanceof String name) {
				names.add(name.trim());
			} else {
				errors.add("participants", Messages.MISSING_STRING);

				return;
			}
		}

		values.put("participants", names);
	}

	private static void readParticipantIds(Map<String, Object> input, FieldErrors errors, Map<String, Object> values,
			boolean creating) {
		if (!input.containsKey("participantIds")) {
			if (creating) {
				values.put("participantIds", new ArrayList<String>());
			}

			return;
		}

		List<Object> items = Requests.list(input, "participantIds");

		if (items == null) {
			errors.add("participantIds", Messages.expected("array", "undefined"));

			return;
		}

		if (items.size() > PARTICIPANT_MAX) {
			errors.add("participantIds", Messages.tooBigArray(PARTICIPANT_MAX));

			return;
		}

		List<String> ids = new ArrayList<>();

		for (Object item : items) {
			if (item instanceof String id && OBJECT_ID.matcher(id).matches()) {
				ids.add(id);
			} else {
				errors.add("participantIds", OBJECT_ID_MESSAGE);

				return;
			}
		}

		if (ids.stream().distinct().count() != ids.size()) {
			errors.add("participantIds", "Select each person only once.");

			return;
		}

		values.put("participantIds", ids);
	}

	private static void readInstant(Map<String, Object> input, String key, FieldErrors errors,
			Map<String, Object> values, boolean creating) {
		if (!input.containsKey(key)) {
			if (creating) {
				errors.add(key, Messages.MISSING_DATE);
			}

			return;
		}

		Instant value = Requests.date(input, key);

		if (value == null) {
			errors.add(key, Messages.MISSING_DATE);

			return;
		}

		values.put(key, value);
	}

	private static void readBoolean(Map<String, Object> input, String key, FieldErrors errors,
			Map<String, Object> values, boolean creating) {
		if (!input.containsKey(key)) {
			if (creating) {
				values.put(key, false);
			}

			return;
		}

		Boolean value = Requests.bool(input, key);

		if (value == null) {
			errors.add(key, Messages.expected("boolean", "undefined"));

			return;
		}

		values.put(key, value);
	}

	private static void readColor(Map<String, Object> input, FieldErrors errors, Map<String, Object> values) {
		if (!input.containsKey("color")) {
			return;
		}

		Object raw = input.get("color");

		if (raw == null) {
			values.put("color", null);

			return;
		}

		String color = Requests.string(input, "color");

		if (color == null || !COLOR.matcher(color).matches()) {
			errors.add("color", COLOR_MESSAGE);

			return;
		}

		values.put("color", color);
	}

	private static void readRecurrence(Map<String, Object> input, FieldErrors errors, Map<String, Object> values) {
		if (!input.containsKey("recurrence")) {
			return;
		}

		Object raw = input.get("recurrence");

		if (!(raw instanceof Map<?, ?> source)) {
			errors.add("recurrence", Messages.expected("object", "undefined"));

			return;
		}

		Map<String, Object> fields = new LinkedHashMap<>();
		source.forEach((key, value) -> fields.put(String.valueOf(key), value));

		Recurrence recurrence = new Recurrence();
		String frequency = Requests.string(fields, "frequency");

		if (frequency == null || !FREQUENCIES.contains(frequency)) {
			errors.add("recurrence", Messages.invalidOption(FREQUENCIES));

			return;
		}

		recurrence.setFrequency(frequency);
		recurrence.setInterval(readBounded(fields, "interval", 1, INTERVAL_MIN, INTERVAL_MAX, errors));
		recurrence.setDaysOfWeek(readDaysOfWeek(fields, errors));
		recurrence.setMonthlyMode(readOption(fields, "monthlyMode", "ordinalWeekday", MONTHLY_MODES, errors));
		recurrence.setEndType(readOption(fields, "endType", "never", END_TYPES, errors));
		recurrence.setCount(fields.get("count") == null ? null
				: readBounded(fields, "count", COUNT_MIN, COUNT_MIN, COUNT_MAX, errors));
		recurrence.setUntil(fields.get("until") == null ? null : Requests.date(fields, "until"));
		recurrence.setTimeZone(Requests.has(fields, "timeZone") ? Requests.string(fields, "timeZone") : "UTC");

		refineRecurrence(recurrence, errors);
		values.put("recurrence", recurrence);
	}

	private static void refineRecurrence(Recurrence recurrence, FieldErrors errors) {
		if ("weekly".equals(recurrence.getFrequency()) && recurrence.getDaysOfWeek().isEmpty()) {
			errors.add("recurrence", "Choose at least one weekday.");
		}

		if ("count".equals(recurrence.getEndType()) && recurrence.getCount() == null) {
			errors.add("recurrence", "Enter an occurrence count.");
		}

		if ("until".equals(recurrence.getEndType()) && recurrence.getUntil() == null) {
			errors.add("recurrence", "Choose an end date.");
		}

		if (recurrence.getTimeZone() == null || !TimeZones.isTimeZone(recurrence.getTimeZone())) {
			errors.add("recurrence", "Choose a valid time zone.");
		}
	}

	private static List<Integer> readDaysOfWeek(Map<String, Object> fields, FieldErrors errors) {
		List<Object> items = Requests.list(fields, "daysOfWeek");

		if (items == null) {
			return new ArrayList<>();
		}

		List<Integer> days = new ArrayList<>();

		for (Object item : items) {
			if (item instanceof Number number && number.intValue() >= 0 && number.intValue() <= 6) {
				days.add(number.intValue());
			} else {
				errors.add("recurrence", Messages.expected("number", "invalid"));

				return days;
			}
		}

		return days;
	}

	private static String readOption(Map<String, Object> fields, String key, String fallback, List<String> options,
			FieldErrors errors) {
		if (!Requests.has(fields, key)) {
			return fallback;
		}

		String value = Requests.string(fields, key);

		if (value == null || !options.contains(value)) {
			errors.add("recurrence", Messages.invalidOption(options));

			return fallback;
		}

		return value;
	}

	private static int readBounded(Map<String, Object> fields, String key, int fallback, int minimum, int maximum,
			FieldErrors errors) {
		if (!Requests.has(fields, key)) {
			return fallback;
		}

		Integer value = Requests.integer(fields, key);

		if (value == null) {
			errors.add("recurrence", Messages.MISSING_NUMBER);

			return fallback;
		}

		if (value < minimum) {
			errors.add("recurrence", Messages.tooSmallNumber(minimum));
		} else if (value > maximum) {
			errors.add("recurrence", Messages.tooBigNumber(maximum));
		}

		return value;
	}

	private static String readSearchText(Map<String, Object> input, String key, FieldErrors errors) {
		if (!input.containsKey(key)) {
			return null;
		}

		String value = Requests.string(input, key);

		if (value == null) {
			errors.add(key, Messages.MISSING_STRING);

			return null;
		}

		String trimmed = value.trim();

		if (trimmed.length() > SEARCH_TEXT_MAX) {
			errors.add(key, Requests.tooBigString(SEARCH_TEXT_MAX));
		}

		return trimmed;
	}

	private static Instant readDate(Map<String, Object> input, String key, FieldErrors errors, boolean required) {
		if (!input.containsKey(key)) {
			if (required) {
				errors.add(key, Messages.MISSING_DATE);
			}

			return null;
		}

		Instant value = Requests.date(input, key);

		if (value == null) {
			errors.add(key, Messages.MISSING_DATE);
		}

		return value;
	}

	private static List<String> readCalendarIds(Map<String, Object> input, FieldErrors errors) {
		List<String> ids = Requests.commaSeparated(input, "calendarIds");

		for (String id : ids) {
			if (!OBJECT_ID.matcher(id).matches()) {
				errors.add("calendarIds", OBJECT_ID_MESSAGE);

				return List.of();
			}
		}

		return ids;
	}

	private EventValidator() {
	}
}
