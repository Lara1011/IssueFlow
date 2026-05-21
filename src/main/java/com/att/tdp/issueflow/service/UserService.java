package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

	private static final String DEFAULT_PASSWORD = "secret";

	private final UserRepository userRepository;
	private final AuditLogService auditLogService;
	private final PasswordEncoder passwordEncoder;

	public UserService(
		UserRepository userRepository,
		AuditLogService auditLogService,
		PasswordEncoder passwordEncoder
	) {
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> getAllUsers() {
		return userRepository.findAll()
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public UserResponse getUserById(Long userId) {
		return toResponse(findUser(userId));
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new IllegalArgumentException("Username already exists");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new IllegalArgumentException("Email already exists");
		}

		User user = new User();
		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setFullName(request.fullName());
		user.setRole(request.role());
		user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));

		User savedUser = userRepository.save(user);
		auditLogService.record(AuditAction.CREATE, AuditEntityType.USER, savedUser.getId(), null, AuditActor.USER);
		return toResponse(savedUser);
	}

	@Transactional
	public void updateUser(Long userId, UpdateUserRequest request) {
		User user = findUser(userId);
		user.setFullName(request.fullName());
		user.setRole(request.role());
		auditLogService.record(AuditAction.UPDATE, AuditEntityType.USER, user.getId(), null, AuditActor.USER);
	}

	@Transactional
	public void deleteUser(Long userId) {
		User user = findUser(userId);
		userRepository.delete(user);
		auditLogService.record(AuditAction.DELETE, AuditEntityType.USER, userId, null, AuditActor.USER);
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
