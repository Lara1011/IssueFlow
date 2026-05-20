package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
	@NotBlank String username,
	@NotBlank @Email String email,
	@NotBlank String fullName,
	@NotNull UserRole role
) {
}
