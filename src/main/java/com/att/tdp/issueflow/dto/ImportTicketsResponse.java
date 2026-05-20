package com.att.tdp.issueflow.dto;

import java.util.List;

public record ImportTicketsResponse(
	int created,
	int failed,
	List<ImportErrorResponse> errors
) {
}
