package com.att.tdp.issueflow.security;

import com.att.tdp.issueflow.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final JwtProperties jwtProperties;
	private final ObjectMapper objectMapper;

	public JwtTokenService(JwtProperties jwtProperties, ObjectMapper objectMapper) {
		this.jwtProperties = jwtProperties;
		this.objectMapper = objectMapper;
	}

	public String generateToken(User user) {
		Instant now = Instant.now();
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sub", user.getUsername());
		payload.put("userId", user.getId());
		payload.put("role", user.getRole().name());
		payload.put("iat", now.getEpochSecond());
		payload.put("exp", now.plusSeconds(jwtProperties.expiresInSeconds()).getEpochSecond());

		String unsignedToken = base64Url(json(header)) + "." + base64Url(json(payload));
		return unsignedToken + "." + sign(unsignedToken);
	}

	public AuthenticatedUser parseToken(String token) {
		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			throw new JwtAuthenticationException("Invalid token");
		}

		String unsignedToken = parts[0] + "." + parts[1];
		if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
			throw new JwtAuthenticationException("Invalid token");
		}

		Map<String, Object> payload = parsePayload(parts[1]);
		long expiresAt = longClaim(payload, "exp");
		if (Instant.now().getEpochSecond() >= expiresAt) {
			throw new JwtAuthenticationException("Token has expired");
		}

		return new AuthenticatedUser(
			longClaim(payload, "userId"),
			stringClaim(payload, "sub"),
			com.att.tdp.issueflow.enums.UserRole.valueOf(stringClaim(payload, "role")),
			token
		);
	}

	public long expiresInSeconds() {
		return jwtProperties.expiresInSeconds();
	}

	private String json(Map<String, Object> value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("JWT payload could not be created", exception);
		}
	}

	private Map<String, Object> parsePayload(String payloadPart) {
		try {
			String json = new String(Base64.getUrlDecoder().decode(payloadPart), StandardCharsets.UTF_8);
			return objectMapper.readValue(json, MAP_TYPE);
		}
		catch (IllegalArgumentException | JsonProcessingException exception) {
			throw new JwtAuthenticationException("Invalid token");
		}
	}

	private String sign(String unsignedToken) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception exception) {
			throw new IllegalStateException("JWT token could not be signed", exception);
		}
	}

	private String base64Url(String value) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private boolean constantTimeEquals(String expected, String actual) {
		return MessageDigest.isEqual(
			expected.getBytes(StandardCharsets.UTF_8),
			actual.getBytes(StandardCharsets.UTF_8)
		);
	}

	private String stringClaim(Map<String, Object> payload, String name) {
		Object value = payload.get(name);
		if (value == null) {
			throw new JwtAuthenticationException("Invalid token");
		}
		return value.toString();
	}

	private long longClaim(Map<String, Object> payload, String name) {
		Object value = payload.get(name);
		if (value instanceof Number number) {
			return number.longValue();
		}
		try {
			return Long.parseLong(String.valueOf(value));
		}
		catch (NumberFormatException exception) {
			throw new JwtAuthenticationException("Invalid token");
		}
	}
}
