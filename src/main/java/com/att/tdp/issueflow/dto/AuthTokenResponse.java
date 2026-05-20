package com.att.tdp.issueflow.dto;

public record AuthTokenResponse(
	String accessToken,
	String tokenType,
	long expiresIn
) {
}
