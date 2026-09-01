package com.calendar.auth;

import java.util.Map;
import java.util.regex.Pattern;

import com.calendar.shared.FieldErrors;
import com.calendar.shared.Messages;
import com.calendar.shared.Requests;

public final class AuthValidator {

	private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private static final Pattern OBJECT_ID = Pattern.compile("^[a-fA-F\\d]{24}$");

	private static final int EMAIL_MAX = 254;

	private static final int PASSWORD_MAX = 200;

	public record Credentials(String email, String password) {
	}

	public static Credentials validateLogin(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		String email = Requests.string(input, "email");
		String password = Requests.string(input, "password");

		if (email == null) {
			errors.add("email", Messages.MISSING_STRING);
		} else if (!EMAIL.matcher(email).matches()) {
			errors.add("email", Messages.INVALID_EMAIL);
		} else if (email.length() > EMAIL_MAX) {
			errors.add("email", Requests.tooBigString(EMAIL_MAX));
		}

		if (password == null) {
			errors.add("password", Messages.MISSING_STRING);
		} else if (password.isEmpty()) {
			errors.add("password", Requests.tooSmallString(1));
		} else if (password.length() > PASSWORD_MAX) {
			errors.add("password", Requests.tooBigString(PASSWORD_MAX));
		}

		errors.throwIfAny();

		return new Credentials(email.toLowerCase(), password);
	}

	public static String validateProfileId(Map<String, Object> body) {
		Map<String, Object> input = Requests.body(body);
		FieldErrors errors = new FieldErrors();
		String profileId = Requests.string(input, "profileId");

		if (profileId == null) {
			errors.add("profileId", Messages.MISSING_STRING);
		} else if (!OBJECT_ID.matcher(profileId).matches()) {
			errors.add("profileId", "Select a valid profile.");
		}

		errors.throwIfAny();

		return profileId;
	}

	private AuthValidator() {
	}
}
