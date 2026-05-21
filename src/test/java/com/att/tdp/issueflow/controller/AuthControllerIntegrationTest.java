package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.LoginRequest;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.JwtProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc

class AuthControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private JwtProperties jwtProperties;

	@Autowired
	private AttachmentRepository attachmentRepository;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private CommentMentionRepository commentMentionRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private TicketDependencyRepository ticketDependencyRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		attachmentRepository.deleteAll();
		commentMentionRepository.deleteAll();
		commentRepository.deleteAll();
		ticketDependencyRepository.deleteAll();
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
		auditLogRepository.deleteAll();
	}

	@Test
	void createUserStillWorksWithoutToken() throws Exception {
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
			.andExpect(jsonPath("$.username").value("jdoe"))
			.andExpect(jsonPath("$.email").value("jdoe@example.com"));

		User user = userRepository.findByUsername("jdoe").orElseThrow();
		Assertions.assertThat(passwordEncoder.matches("secret", user.getPasswordHash())).isTrue();
	}

	@Test
	void loginWithValidCredentialsReturnsToken() throws Exception {
		saveUser("jdoe", "secret");

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest("jdoe", "secret"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken", not(emptyString())))
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresIn").value(3600));
	}

	@Test
	void loginWithUnknownUsernameReturnsUnauthorized() throws Exception {
		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest("missing", "secret"))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid username or password"));
	}

	@Test
	void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
		saveUser("jdoe", "secret");

		mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest("jdoe", "wrong"))))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid username or password"));
	}

	@Test
	void getCurrentUserWithValidTokenReturnsProfile() throws Exception {
		User user = saveUser("jdoe", "secret");
		String token = login("jdoe", "secret");

		mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId()))
			.andExpect(jsonPath("$.username").value("jdoe"))
			.andExpect(jsonPath("$.email").value("jdoe@example.com"))
			.andExpect(jsonPath("$.fullName").value("John Doe"))
			.andExpect(jsonPath("$.role").value("DEVELOPER"));
	}

	@Test
	void getCurrentUserWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Authorization header is required"));
	}

	@Test
	void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/users"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Authorization header is required"));
	}

	@Test
	void protectedEndpointWithValidTokenSucceeds() throws Exception {
		saveUser("jdoe", "secret");
		String token = login("jdoe", "secret");

		mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].username").value("jdoe"));
	}

	@Test
	void logoutInvalidatesToken() throws Exception {
		saveUser("jdoe", "secret");
		String token = login("jdoe", "secret");

		mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Token has been logged out"));
	}

	@Test
	void loggedOutTokenReturnsUnauthorized() throws Exception {
		saveUser("jdoe", "secret");
		String token = login("jdoe", "secret");

		mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());

		mockMvc.perform(get("/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Token has been logged out"));
	}

	@Test
	void successfulLoginCreatesAuditLog() throws Exception {
		User user = saveUser("jdoe", "secret");

		login("jdoe", "secret");

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.LOGIN);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.AUTH);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(user.getId());
		Assertions.assertThat(auditLog.getPerformedBy()).isEqualTo(user.getId());
	}

	@Test
	void successfulLogoutCreatesAuditLog() throws Exception {
		User user = saveUser("jdoe", "secret");
		String token = login("jdoe", "secret");
		auditLogRepository.deleteAll();

		mockMvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.LOGOUT);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.AUTH);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(user.getId());
		Assertions.assertThat(auditLog.getPerformedBy()).isEqualTo(user.getId());
	}

	@Test
	void invalidTokenReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid token"));
	}

	@Test
	void expiredTokenReturnsUnauthorizedWithInformativeMessage() throws Exception {
		String expiredToken = expiredToken();

		mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("expired")))
			.andExpect(jsonPath("$.message").value("Token has expired"));
	}

	private String login(String username, String password) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		return json.get("accessToken").asText();
	}

	private AuditLog singleAuditLog() {
		List<AuditLog> auditLogs = auditLogRepository.findAll();
		Assertions.assertThat(auditLogs).hasSize(1);
		return auditLogs.getFirst();
	}

	private String expiredToken() throws Exception {
		Instant now = Instant.now();
		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", "HS256");
		header.put("typ", "JWT");

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("sub", "jdoe");
		payload.put("userId", 1L);
		payload.put("role", UserRole.DEVELOPER.name());
		payload.put("iat", now.minusSeconds(7200).getEpochSecond());
		payload.put("exp", now.minusSeconds(3600).getEpochSecond());

		String unsignedToken = base64Url(objectMapper.writeValueAsString(header))
			+ "."
			+ base64Url(objectMapper.writeValueAsString(payload));
		return unsignedToken + "." + sign(unsignedToken);
	}

	private String sign(String unsignedToken) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
	}

	private String base64Url(String value) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

	private User saveUser(String username, String password) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(username + "@example.com");
		user.setFullName("John Doe");
		user.setRole(UserRole.DEVELOPER);
		user.setPasswordHash(passwordEncoder.encode(password));
		return userRepository.save(user);
	}
}
