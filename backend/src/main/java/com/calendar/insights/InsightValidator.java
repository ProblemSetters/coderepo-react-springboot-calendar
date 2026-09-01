package com.calendar.insights;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.calendar.shared.FieldErrors;
import com.calendar.shared.Messages;
import com.calendar.shared.Requests;

public final class InsightValidator {

	private static final Pattern OBJECT_ID = Pattern.compile("^[0-9a-fA-F]{24}$");

	private static final String OBJECT_ID_MESSAGE = "Invalid string: must match pattern /^[0-9a-fA-F]{24}$/";

	public record DailyQuery(Instant from, Instant to, List<String> calendarIds) {
	}

	public static DailyQuery validateDaily(Map<String, String> parameters) {
		Map<String, Object> input = Requests.query(parameters);
		FieldErrors errors = new FieldErrors();
		Instant from = readDate(input, "from", errors);
		Instant to = readDate(input, "to", errors);
		List<String> calendarIds = Requests.commaSeparated(input, "calendarIds");

		for (String id : calendarIds) {
			if (!OBJECT_ID.matcher(id).matches()) {
				errors.add("calendarIds", OBJECT_ID_MESSAGE);

				break;
			}
		}

		errors.throwIfAny();

		if (!from.isBefore(to)) {
			errors.add("to", "to must be after from");
		}

		errors.throwIfAny();

		return new DailyQuery(from, to, calendarIds);
	}

	private static Instant readDate(Map<String, Object> input, String key, FieldErrors errors) {
		Instant value = Requests.date(input, key);

		if (value == null) {
			errors.add(key, Messages.MISSING_DATE);
		}

		return value;
	}

	private InsightValidator() {
	}
}
