package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
	@NotBlank(message = "username is required") String username,
	@NotBlank(message = "email is required") @Email(message = "email must be a valid email address") String email,
	@NotBlank(message = "fullName is required") String fullName,
	@NotNull(message = "role is required") UserRole role
) {
}
