package com.calendar.people;

import java.util.Map;

import com.calendar.shared.FieldErrors;
import com.calendar.shared.Messages;
import com.calendar.shared.Requests;

public final class PersonValidator {

	private static final int QUERY_MAX = 100;

	private static final int LIMIT_MIN = 1;

	private static final int LIMIT_MAX = 50;

	private static final int LIMIT_DEFAULT = 20;

	public record Search(String query, int limit) {
	}

	public static Search validateSearch(Map<String, String> parameters) {
		Map<String, Object> input = Requests.query(parameters);
		FieldErrors errors = new FieldErrors();
		String query = Requests.has(input, "q") ? Requests.string(input, "q").trim() : "";
		Integer limit = Requests.has(input, "limit") ? Requests.integer(input, "limit") : LIMIT_DEFAULT;

		if (query.length() > QUERY_MAX) {
			errors.add("q", Requests.tooBigString(QUERY_MAX));
		}

		if (limit == null) {
			errors.add("limit", Messages.MISSING_NUMBER);
		} else if (limit < LIMIT_MIN) {
			errors.add("limit", Messages.tooSmallNumber(LIMIT_MIN));
		} else if (limit > LIMIT_MAX) {
			errors.add("limit", Messages.tooBigNumber(LIMIT_MAX));
		}

		errors.throwIfAny();

		return new Search(query, limit);
	}

	private PersonValidator() {
	}
}
