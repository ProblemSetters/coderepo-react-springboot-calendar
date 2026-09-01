package com.calendar.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

@Service
public class JwtService {

	private static final String ISSUER = "calendar-api";

	private static final String AUDIENCE = "calendar-app";

	private static final int SECONDS_PER_HOUR = 3600;

	private static final int SECONDS_PER_MINUTE = 60;

	private static final int SECONDS_PER_DAY = 86400;

	public record Payload(String subject, String type, String profileId) {
	}

	private final Algorithm algorithm;

	private final JWTVerifier verifier;

	private final Duration expiresIn;

	public JwtService(@Value("${calendar.jwt.secret}") String secret,
			@Value("${calendar.jwt.expires-in}") String expiresIn) {
		this.algorithm = Algorithm.HMAC256(secret);
		this.verifier = JWT.require(algorithm).withIssuer(ISSUER).withAudience(AUDIENCE).build();
		this.expiresIn = parseDuration(expiresIn);
	}

	public String issue(String accountId, String profileId) {
		Instant now = Instant.now();
		var builder = JWT.create().withSubject(accountId)
				.withClaim("type", profileId == null ? "workspace" : "profile");

		if (profileId != null) {
			builder.withClaim("profileId", profileId);
		}

		return builder.withIssuedAt(Date.from(now)).withExpiresAt(Date.from(now.plus(expiresIn))).withIssuer(ISSUER)
				.withAudience(AUDIENCE).sign(algorithm);
	}

	public Payload read(String token) {
		DecodedJWT decoded = verifier.verify(token);

		return new Payload(decoded.getSubject(), decoded.getClaim("type").asString(),
				decoded.getClaim("profileId").asString());
	}

	private static Duration parseDuration(String value) {
		String trimmed = value.trim();
		char unit = trimmed.charAt(trimmed.length() - 1);

		if (!Character.isLetter(unit)) {
			return Duration.ofSeconds(Long.parseLong(trimmed));
		}

		long amount = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));

		return Duration.ofSeconds(amount * secondsFor(unit));
	}

	private static int secondsFor(char unit) {
		return switch (unit) {
			case 'd' -> SECONDS_PER_DAY;
			case 'h' -> SECONDS_PER_HOUR;
			case 'm' -> SECONDS_PER_MINUTE;
			default -> 1;
		};
	}
}
