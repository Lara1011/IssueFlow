package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.UserRole;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
	@NotBlank String fullName,
	UserRole role
) {
}
