package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.LoginRequest;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
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
class AuthorizationHardeningIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PasswordEncoder passwordEncoder;

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
	void adminCanAccessDeletedProjectsList() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		saveProject(admin.getId(), Instant.now());

		mockMvc.perform(get("/projects/deleted").header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].ownerId").value(admin.getId()));
	}

	@Test
	void developerCannotAccessDeletedProjectsList() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		User developer = saveUser("developer", UserRole.DEVELOPER);
		saveProject(admin.getId(), Instant.now());

		mockMvc.perform(get("/projects/deleted").header(HttpHeaders.AUTHORIZATION, bearerToken(developer)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("ADMIN")));
	}

	@Test
	void adminCanRestoreProject() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		Project project = saveProject(admin.getId(), Instant.now());

		mockMvc.perform(post("/projects/{projectId}/restore", project.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
			.andExpect(status().isOk());

		Assertions.assertThat(projectRepository.findById(project.getId()).orElseThrow().getDeletedAt()).isNull();
	}

	@Test
	void developerCannotRestoreProject() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		User developer = saveUser("developer", UserRole.DEVELOPER);
		Project project = saveProject(admin.getId(), Instant.now());

		mockMvc.perform(post("/projects/{projectId}/restore", project.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(developer)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("ADMIN")));
	}

	@Test
	void adminCanAccessDeletedTicketsList() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		Project project = saveProject(admin.getId(), null);
		saveTicket(project.getId(), Instant.now());

		mockMvc.perform(get("/tickets/deleted")
				.param("projectId", project.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].projectId").value(project.getId()));
	}

	@Test
	void developerCannotAccessDeletedTicketsList() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		User developer = saveUser("developer", UserRole.DEVELOPER);
		Project project = saveProject(admin.getId(), null);
		saveTicket(project.getId(), Instant.now());

		mockMvc.perform(get("/tickets/deleted")
				.param("projectId", project.getId().toString())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(developer)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("ADMIN")));
	}

	@Test
	void adminCanRestoreTicket() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		Project project = saveProject(admin.getId(), null);
		Ticket ticket = saveTicket(project.getId(), Instant.now());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticket.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin)))
			.andExpect(status().isOk());

		Assertions.assertThat(ticketRepository.findById(ticket.getId()).orElseThrow().getDeletedAt()).isNull();
	}

	@Test
	void developerCannotRestoreTicket() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		User developer = saveUser("developer", UserRole.DEVELOPER);
		Project project = saveProject(admin.getId(), null);
		Ticket ticket = saveTicket(project.getId(), Instant.now());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticket.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(developer)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("ADMIN")));
	}

	@Test
	void authenticatedProjectCreateRecordsPerformedByCurrentUserId() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		auditLogRepository.deleteAll();
		CreateProjectRequest request = new CreateProjectRequest("Project", "Description", admin.getId());

		mockMvc.perform(post("/projects")
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog(AuditAction.CREATE, AuditEntityType.PROJECT);
		Assertions.assertThat(auditLog.getPerformedBy()).isEqualTo(admin.getId());
	}

	@Test
	void authenticatedProjectUpdateRecordsPerformedByCurrentUserId() throws Exception {
		User admin = saveUser("admin", UserRole.ADMIN);
		Project project = saveProject(admin.getId(), null);
		auditLogRepository.deleteAll();
		UpdateProjectRequest request = new UpdateProjectRequest("Updated", null);

		mockMvc.perform(patch("/projects/{projectId}", project.getId())
				.header(HttpHeaders.AUTHORIZATION, bearerToken(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog(AuditAction.UPDATE, AuditEntityType.PROJECT);
		Assertions.assertThat(auditLog.getPerformedBy()).isEqualTo(admin.getId());
	}

	private String bearerToken(User user) throws Exception {
		return "Bearer " + login(user.getUsername());
	}

	private String login(String username) throws Exception {
		MvcResult result = mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new LoginRequest(username, "secret"))))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
		return json.get("accessToken").asText();
	}

	private AuditLog singleAuditLog(AuditAction action, AuditEntityType entityType) {
		List<AuditLog> auditLogs = auditLogRepository.findAll()
			.stream()
			.filter(log -> log.getAction() == action && log.getEntityType() == entityType)
			.toList();
		Assertions.assertThat(auditLogs).hasSize(1);
		return auditLogs.getFirst();
	}

	private User saveUser(String username, UserRole role) {
		User user = new User();
		user.setUsername(username + System.nanoTime());
		user.setEmail(username + System.nanoTime() + "@example.com");
		user.setFullName("Test User");
		user.setRole(role);
		user.setPasswordHash(passwordEncoder.encode("secret"));
		return userRepository.save(user);
	}

	private Project saveProject(Long ownerId, Instant deletedAt) {
		Project project = new Project();
		project.setName("Project " + System.nanoTime());
		project.setDescription("Description");
		project.setOwnerId(ownerId);
		project.setDeletedAt(deletedAt);
		return projectRepository.save(project);
	}

	private Ticket saveTicket(Long projectId, Instant deletedAt) {
		Ticket ticket = new Ticket();
		ticket.setTitle("Ticket " + System.nanoTime());
		ticket.setDescription("Description");
		ticket.setStatus(TicketStatus.TODO);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		ticket.setDeletedAt(deletedAt);
		return ticketRepository.save(ticket);
	}
}
