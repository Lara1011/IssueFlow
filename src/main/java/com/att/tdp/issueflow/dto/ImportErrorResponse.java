package com.att.tdp.issueflow.dto;

public record ImportErrorResponse(
	int row,
	String message
) {
}
