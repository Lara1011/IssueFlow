package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.dto.UserResponse;
import com.att.tdp.issueflow.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserResponse> getAllUsers() {
		return userService.getAllUsers();
	}

	@GetMapping("/{userId}")
	public UserResponse getUserById(@PathVariable Long userId) {
		return userService.getUserById(userId);
	}

	@PostMapping
	public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
		return userService.createUser(request);
	}

	@PostMapping("/update/{userId}")
	public void updateUser(
		@PathVariable Long userId,
		@Valid @RequestBody UpdateUserRequest request
	) {
		userService.updateUser(userId, request);
	}

	@DeleteMapping("/{userId}")
	public void deleteUser(@PathVariable Long userId) {
		userService.deleteUser(userId);
	}
}
