package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
	@NotBlank(message = "fullName is required") String fullName,
	@NotNull(message = "role is required") UserRole role
) {
}
