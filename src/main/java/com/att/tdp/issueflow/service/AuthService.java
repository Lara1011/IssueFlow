package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AuthTokenResponse;
import com.att.tdp.issueflow.dto.LoginRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.AuthenticationFailedException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.AuthenticatedUser;
import com.att.tdp.issueflow.security.JwtTokenService;
import com.att.tdp.issueflow.security.SecurityConstants;
import com.att.tdp.issueflow.security.TokenDenyListService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;
	private final TokenDenyListService tokenDenyListService;
	private final AuditLogService auditLogService;

	public AuthService(
		UserRepository userRepository,
		PasswordEncoder passwordEncoder,
		JwtTokenService jwtTokenService,
		TokenDenyListService tokenDenyListService,
		AuditLogService auditLogService
	) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
		this.tokenDenyListService = tokenDenyListService;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public AuthTokenResponse login(LoginRequest request) {
		User user = userRepository.findByUsername(request.username())
			.orElseThrow(() -> new AuthenticationFailedException("Invalid username or password"));
		if (user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			throw new AuthenticationFailedException("Invalid username or password");
		}

		String token = jwtTokenService.generateToken(user);
		auditLogService.record(AuditAction.LOGIN, AuditEntityType.AUTH, user.getId(), user.getId(), AuditActor.USER);
		return new AuthTokenResponse(token, SecurityConstants.TOKEN_TYPE, jwtTokenService.expiresInSeconds());
	}

	@Transactional(readOnly = true)
	public UserResponse getCurrentUser(AuthenticatedUser authenticatedUser) {
		User user = findUser(authenticatedUser.id());
		return toResponse(user);
	}

	@Transactional
	public void logout(AuthenticatedUser authenticatedUser) {
		tokenDenyListService.deny(authenticatedUser.token());
		auditLogService.record(
			AuditAction.LOGOUT,
			AuditEntityType.AUTH,
			authenticatedUser.id(),
			authenticatedUser.id(),
			AuditActor.USER
		);
	}

	private User findUser(Long userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
	}

	private UserResponse toResponse(User user) {
		return new UserResponse(
			user.getId(),
			user.getUsername(),
			user.getEmail(),
			user.getFullName(),
			user.getRole()
		);
	}
}
