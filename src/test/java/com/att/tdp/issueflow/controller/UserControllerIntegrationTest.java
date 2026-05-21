package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class UserControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
	}

	@Test
	void createUserSuccessfully() throws Exception {
		CreateUserRequest request = new CreateUserRequest(
			"jdoe",
			"jdoe@example.com",
			"John Doe",
			UserRole.DEVELOPER
		);

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.username").value("jdoe"))
			.andExpect(jsonPath("$.email").value("jdoe@example.com"))
			.andExpect(jsonPath("$.fullName").value("John Doe"))
			.andExpect(jsonPath("$.role").value("DEVELOPER"));
	}

	@Test
	void getAllUsers() throws Exception {
		saveUser("jdoe", "jdoe@example.com", "John Doe", UserRole.DEVELOPER);
		saveUser("admin", "admin@example.com", "Admin User", UserRole.ADMIN);

		mockMvc.perform(get("/users"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].username", containsInAnyOrder("jdoe", "admin")))
			.andExpect(jsonPath("$[*].email", containsInAnyOrder("jdoe@example.com", "admin@example.com")));
	}

	@Test
	void getUserById() throws Exception {
		User user = saveUser("jdoe", "jdoe@example.com", "John Doe", UserRole.DEVELOPER);

		mockMvc.perform(get("/users/{userId}", user.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId()))
			.andExpect(jsonPath("$.username").value("jdoe"))
			.andExpect(jsonPath("$.email").value("jdoe@example.com"))
			.andExpect(jsonPath("$.fullName").value("John Doe"))
			.andExpect(jsonPath("$.role").value("DEVELOPER"));
	}

	@Test
	void updateUserFullNameAndRole() throws Exception {
		User user = saveUser("jdoe", "jdoe@example.com", "John Doe", UserRole.DEVELOPER);
		UpdateUserRequest request = new UpdateUserRequest("Jane Doe", UserRole.ADMIN);

		mockMvc.perform(post("/users/update/{userId}", user.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/users/{userId}", user.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fullName").value("Jane Doe"))
			.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void updateUserIgnoresUsernameAndEmailFields() throws Exception {
		User user = saveUser("jdoe", "jdoe@example.com", "John Doe", UserRole.DEVELOPER);
		Map<String, String> request = Map.of(
			"username", "changed",
			"email", "changed@example.com",
			"fullName", "Jane Doe",
			"role", "ADMIN"
		);

		mockMvc.perform(post("/users/update/{userId}", user.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/users/{userId}", user.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("jdoe"))
			.andExpect(jsonPath("$.email").value("jdoe@example.com"))
			.andExpect(jsonPath("$.fullName").value("Jane Doe"))
			.andExpect(jsonPath("$.role").value("ADMIN"));
	}

	@Test
	void deleteUser() throws Exception {
		User user = saveUser("jdoe", "jdoe@example.com", "John Doe", UserRole.DEVELOPER);

		mockMvc.perform(delete("/users/{userId}", user.getId()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/users/{userId}", user.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("User not found: " + user.getId()));
	}

	@Test
	void rejectInvalidEmail() throws Exception {
		CreateUserRequest request = new CreateUserRequest(
			"jdoe",
			"not-an-email",
			"John Doe",
			UserRole.DEVELOPER
		);

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.fieldErrors.email").value("email must be a valid email address"));
	}

	@Test
	void rejectInvalidRoleWithInformativeMessage() throws Exception {
		String request = """
			{
			  "username": "badrole",
			  "email": "badrole@example.com",
			  "fullName": "Bad Role",
			  "role": "MANAGER"
			}
			""";

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.status").value(400))
			.andExpect(jsonPath("$.error").value("Bad Request"))
			.andExpect(jsonPath("$.message").value("role must be one of: ADMIN, DEVELOPER"));
	}

	@Test
	void rejectBlankUserFieldsAndMissingRoleWithInformativeMessages() throws Exception {
		String request = """
			{
			  "username": " ",
			  "email": " ",
			  "fullName": "",
			  "role": null
			}
			""";

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.fieldErrors.username").value("username is required"))
			.andExpect(jsonPath("$.fieldErrors.email").exists())
			.andExpect(jsonPath("$.fieldErrors.fullName").value("fullName is required"))
			.andExpect(jsonPath("$.fieldErrors.role").value("role is required"));
	}

	@Test
	void rejectDuplicateUsername() throws Exception {
		saveUser("jdoe", "jdoe@example.com", "John Doe", UserRole.DEVELOPER);
		CreateUserRequest request = new CreateUserRequest(
			"jdoe",
			"other@example.com",
			"Other User",
			UserRole.DEVELOPER
		);

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Username already exists"));
	}

	@Test
	void rejectDuplicateEmail() throws Exception {
		saveUser("jdoe", "jdoe@example.com", "John Doe", UserRole.DEVELOPER);
		CreateUserRequest request = new CreateUserRequest(
			"other",
			"jdoe@example.com",
			"Other User",
			UserRole.DEVELOPER
		);

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Email already exists"));
	}

	@Test
	void returnNotFoundWhenUserDoesNotExist() throws Exception {
		mockMvc.perform(get("/users/{userId}", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.status").value(404))
			.andExpect(jsonPath("$.message").value("User not found: 999"));
	}

	private User saveUser(String username, String email, String fullName, UserRole role) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName(fullName);
		user.setRole(role);
		return userRepository.save(user);
	}
}
