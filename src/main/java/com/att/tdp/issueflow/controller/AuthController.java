package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.AuthTokenResponse;
import com.att.tdp.issueflow.dto.LoginRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/logout")
	public void logout(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		authService.logout(authenticatedUser);
	}

	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
		return authService.getCurrentUser(authenticatedUser);
	}
}
