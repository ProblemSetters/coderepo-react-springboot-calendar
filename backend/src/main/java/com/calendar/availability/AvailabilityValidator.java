package com.calendar.availability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.calendar.shared.FieldErrors;
import com.calendar.shared.Messages;
import com.calendar.shared.Requests;
import com.calendar.shared.TimeZones;

public final class AvailabilityValidator {

	private static final Pattern DATE_KEY = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

	private static final int CONFLICT_PEOPLE_MAX = 100;

	private static final int SUGGESTION_PEOPLE_MAX = 10;

	private static final int DAYS_MIN = 1;

	private static final int DAYS_MAX = 14;

	private static final int DAYS_DEFAULT = 5;

	private static final int DURATION_MIN = 15;

	private static final int DURATION_MAX = 240;

	private static final int DURATION_STEP = 15;

	private static final String TIME_ZONE_MESSAGE = "Select a valid IANA time zone.";

	private static final String DUPLICATE_MESSAGE = "Select each person only once.";

	public record ConflictInput(List<String> participantIds, Instant startAt, Instant endAt, String timeZone) {
	}

	public record SuggestionInput(List<String> participantIds, String from, String timeZone, int days,
			int durationMinutes) {
	}

	public static ConflictInput validateConflicts(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		List<String> participantIds = readParticipantIds(input, errors, CONFLICT_PEOPLE_MAX);
		Instant startAt = readDate(input, "startAt", errors);
		Instant endAt = readDate(input, "endAt", errors);
		String timeZone = readTimeZone(input, errors, "UTC");

		errors.throwIfAny();

		if (!startAt.isBefore(endAt)) {
			errors.add("endAt", "endAt must be after startAt.");
		}

		errors.throwIfAny();

		return new ConflictInput(participantIds, startAt, endAt, timeZone);
	}

	public static SuggestionInput validateSuggestions(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		List<String> participantIds = readParticipantIds(input, errors, SUGGESTION_PEOPLE_MAX);
		String from = Requests.string(input, "from");
		String timeZone = readTimeZone(input, errors, null);
		int days = readBounded(input, "days", DAYS_DEFAULT, DAYS_MIN, DAYS_MAX, errors);
		int durationMinutes = readDuration(input, errors);

		if (from == null) {
			errors.add("from", Messages.MISSING_STRING);
		} else if (!DATE_KEY.matcher(from).matches()) {
			errors.add("from", "Invalid string: must match pattern /^\\d{4}-\\d{2}-\\d{2}$/");
		}

		errors.throwIfAny();

		return new SuggestionInput(participantIds, from, timeZone, days, durationMinutes);
	}

	private static List<String> readParticipantIds(Map<String, Object> input, FieldErrors errors, int maximum) {
		List<Object> items = Requests.list(input, "participantIds");

		if (items == null) {
			errors.add("participantIds", Messages.expected("array", "undefined"));

			return List.of();
		}

		if (items.isEmpty()) {
			errors.add("participantIds", Messages.tooSmallArray(1));

			return List.of();
		}

		if (items.size() > maximum) {
			errors.add("participantIds", Messages.tooBigArray(maximum));

			return List.of();
		}

		List<String> ids = new ArrayList<>();

		for (Object item : items) {
			if (item instanceof String id) {
				ids.add(id);
			} else {
				errors.add("participantIds", Messages.MISSING_STRING);

				return List.of();
			}
		}

		if (ids.stream().distinct().count() != ids.size()) {
			errors.add("participantIds", DUPLICATE_MESSAGE);
		}

		return ids;
	}

	private static Instant readDate(Map<String, Object> input, String key, FieldErrors errors) {
		Instant value = Requests.date(input, key);

		if (value == null) {
			errors.add(key, Messages.MISSING_DATE);
		}

		return value;
	}

	private static String readTimeZone(Map<String, Object> input, FieldErrors errors, String fallback) {
		String value = Requests.string(input, "timeZone");

		if (value == null) {
			if (fallback == null) {
				errors.add("timeZone", Messages.MISSING_STRING);
			}

			return fallback;
		}

		if (!TimeZones.isTimeZone(value)) {
			errors.add("timeZone", TIME_ZONE_MESSAGE);
		}

		return value;
	}

	private static int readBounded(Map<String, Object> input, String key, int fallback, int minimum, int maximum,
			FieldErrors errors) {
		if (!Requests.has(input, key)) {
			return fallback;
		}

		Integer value = Requests.integer(input, key);

		if (value == null) {
			errors.add(key, Messages.MISSING_NUMBER);

			return fallback;
		}

		if (value < minimum) {
			errors.add(key, Messages.tooSmallNumber(minimum));
		} else if (value > maximum) {
			errors.add(key, Messages.tooBigNumber(maximum));
		}

		return value;
	}

	private static int readDuration(Map<String, Object> input, FieldErrors errors) {
		Integer value = Requests.integer(input, "durationMinutes");

		if (value == null) {
			errors.add("durationMinutes", Messages.MISSING_NUMBER);

			return DURATION_MIN;
		}

		if (value < DURATION_MIN) {
			errors.add("durationMinutes", Messages.tooSmallNumber(DURATION_MIN));
		} else if (value > DURATION_MAX) {
			errors.add("durationMinutes", Messages.tooBigNumber(DURATION_MAX));
		} else if (value % DURATION_STEP != 0) {
			errors.add("durationMinutes", "Duration must use 15-minute increments.");
		}

		return value;
	}

	private AvailabilityValidator() {
	}
}
