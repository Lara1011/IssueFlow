package com.att.tdp.issueflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProjectRequest(
	@NotBlank(message = "name is required") String name,
	String description,
	@NotNull(message = "ownerId is required") Long ownerId
) {
}
