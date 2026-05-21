package com.att.tdp.issueflow.dto;

import jakarta.validation.constraints.NotNull;

public record AddDependencyRequest(
	@NotNull(message = "blockedBy is required") Long blockedBy
) {
}
