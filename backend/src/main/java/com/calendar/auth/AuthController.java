package com.calendar.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.calendar.shared.RequestContext;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	private final RequestContext requestContext;

	public AuthController(AuthService authService, RequestContext requestContext) {
		this.authService = authService;
		this.requestContext = requestContext;
	}

	@PostMapping("/login")
	public Map<String, Object> login(@RequestBody(required = false) Map<String, Object> body) {
		AuthValidator.Credentials credentials = AuthValidator.validateLogin(body);

		return Map.of("data", authService.login(credentials.email(), credentials.password()));
	}

	@GetMapping("/session")
	public Map<String, Object> session() {
		return Map.of("data", authService.session(requestContext.account()));
	}

	@PostMapping("/switch-profile")
	public Map<String, Object> switchProfile(@RequestBody(required = false) Map<String, Object> body) {
		String profileId = AuthValidator.validateProfileId(body);

		return Map.of("data", authService.switchProfile(requestContext.account(), profileId));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().build();
	}
}
