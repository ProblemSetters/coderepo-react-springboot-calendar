package com.calendar.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.calendar.people.Person;
import com.calendar.people.PersonService;
import com.calendar.shared.ApiException;
import com.calendar.shared.ObjectIds;

import at.favre.lib.crypto.bcrypt.BCrypt;

@Service
public class AuthService {

	private static final int UNAUTHORIZED = 401;

	private static final int FORBIDDEN = 403;

	private static final int NOT_FOUND = 404;

	private static final String INVALID_TOKEN_MESSAGE = "Your Calendar session is invalid or has expired.";

	private static final String PROFILE_FORBIDDEN_MESSAGE = "This profile is not available in the current workspace.";

	public record Authentication(WorkspaceAccount account, JwtService.Payload payload) {
	}

	private final AuthRepository authRepository;

	private final PersonService personService;

	private final JwtService jwtService;

	public AuthService(AuthRepository authRepository, PersonService personService, JwtService jwtService) {
		this.authRepository = authRepository;
		this.personService = personService;
		this.jwtService = jwtService;
	}

	public Map<String, Object> login(String email, String password) {
		WorkspaceAccount account = authRepository.findActiveByEmail(email.toLowerCase());
		boolean valid = account != null
				&& BCrypt.verifyer().verify(password.toCharArray(), account.getPasswordHash()).verified;

		if (!valid) {
			throw new ApiException(UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("account", account.toPublicMap());
		body.put("token", jwtService.issue(account.getId(), null));

		return body;
	}

	public Authentication authenticate(String token) {
		JwtService.Payload payload = readPayload(token);

		if (!ObjectIds.isValid(payload.subject()) || !isKnownType(payload.type())) {
			throw new ApiException(UNAUTHORIZED, "INVALID_TOKEN", INVALID_TOKEN_MESSAGE);
		}

		WorkspaceAccount account = authRepository.findActiveById(payload.subject());

		if (account == null) {
			throw new ApiException(UNAUTHORIZED, "ACCOUNT_UNAVAILABLE", "This Calendar workspace is no longer available.");
		}

		if ("profile".equals(payload.type()) && !isAllowed(account, payload.profileId())) {
			throw new ApiException(FORBIDDEN, "PROFILE_FORBIDDEN", PROFILE_FORBIDDEN_MESSAGE);
		}

		return new Authentication(account, payload);
	}

	public Map<String, Object> session(WorkspaceAccount account) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("account", account.toPublicMap());

		return body;
	}

	public Map<String, Object> switchProfile(WorkspaceAccount account, String profileId) {
		if (!isAllowed(account, profileId)) {
			throw new ApiException(FORBIDDEN, "PROFILE_FORBIDDEN", PROFILE_FORBIDDEN_MESSAGE);
		}

		Person profile = personService.findProfileById(profileId);

		if (profile == null) {
			throw new ApiException(NOT_FOUND, "PROFILE_NOT_FOUND", "This Calendar profile is no longer available.");
		}

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("profile", profile.toMap());
		body.put("token", jwtService.issue(account.getId(), profileId));

		return body;
	}

	private JwtService.Payload readPayload(String token) {
		try {
			return jwtService.read(token);
		} catch (RuntimeException exception) {
			throw new ApiException(UNAUTHORIZED, "INVALID_TOKEN", INVALID_TOKEN_MESSAGE);
		}
	}

	private boolean isKnownType(String type) {
		return "workspace".equals(type) || "profile".equals(type);
	}

	private boolean isAllowed(WorkspaceAccount account, String profileId) {
		return profileId != null && account.getAllowedProfileIds().contains(profileId);
	}
}
