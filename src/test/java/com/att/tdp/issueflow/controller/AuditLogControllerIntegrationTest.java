package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.CreateCommentRequest;
import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.CreateUserRequest;
import com.att.tdp.issueflow.dto.UpdateUserRequest;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
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
class AuditLogControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private AuditLogRepository auditLogRepository;

	@Autowired
	private CommentMentionRepository commentMentionRepository;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		commentMentionRepository.deleteAll();
		commentRepository.deleteAll();
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
		auditLogRepository.deleteAll();
	}

	@Test
	void creatingUserCreatesAuditLog() throws Exception {
		CreateUserRequest request = new CreateUserRequest(
			"jdoe",
			"jdoe@example.com",
			"John Doe",
			UserRole.DEVELOPER
		);

		mockMvc.perform(post("/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.CREATE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.USER);
		Assertions.assertThat(auditLog.getActor()).isEqualTo(AuditActor.USER);
		Assertions.assertThat(auditLog.getPerformedBy()).isNull();
	}

	@Test
	void updatingUserCreatesAuditLog() throws Exception {
		User user = saveUser("jdoe", "jdoe@example.com");
		UpdateUserRequest request = new UpdateUserRequest("Jane Doe", UserRole.ADMIN);

		mockMvc.perform(post("/users/update/{userId}", user.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.UPDATE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.USER);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(user.getId());
	}

	@Test
	void deletingUserCreatesAuditLog() throws Exception {
		User user = saveUser("jdoe", "jdoe@example.com");

		mockMvc.perform(delete("/users/{userId}", user.getId()))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.DELETE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.USER);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(user.getId());
	}

	@Test
	void creatingProjectCreatesAuditLog() throws Exception {
		User owner = saveUser("owner", "owner@example.com");
		CreateProjectRequest request = new CreateProjectRequest("Sample Project", "A sample project", owner.getId());

		mockMvc.perform(post("/projects")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.CREATE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.PROJECT);
	}

	@Test
	void creatingTicketCreatesAuditLog() throws Exception {
		Project project = saveProject();
		CreateTicketRequest request = new CreateTicketRequest(
			"Fix login bug",
			"Login fails",
			TicketStatus.TODO,
			TicketPriority.HIGH,
			TicketType.BUG,
			project.getId(),
			null,
			null
		);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog(AuditAction.CREATE, AuditEntityType.TICKET);
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.CREATE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.TICKET);
	}

	@Test
	void creatingCommentCreatesAuditLogWithAuthorAsPerformedBy() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), "Hello");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.CREATE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.COMMENT);
		Assertions.assertThat(auditLog.getPerformedBy()).isEqualTo(author.getId());
	}

	@Test
	void getAuditLogsReturnsNewestFirst() throws Exception {
		saveAuditLog(AuditAction.CREATE, AuditEntityType.USER, 1L, AuditActor.USER, Instant.parse("2026-03-01T10:00:00Z"));
		saveAuditLog(AuditAction.UPDATE, AuditEntityType.USER, 1L, AuditActor.USER, Instant.parse("2026-03-01T11:00:00Z"));

		mockMvc.perform(get("/audit-logs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].action").value("UPDATE"))
			.andExpect(jsonPath("$[1].action").value("CREATE"));
	}

	@Test
	void getAuditLogsFiltersByEntityType() throws Exception {
		saveAuditLog(AuditAction.CREATE, AuditEntityType.USER, 1L, AuditActor.USER, Instant.parse("2026-03-01T10:00:00Z"));
		saveAuditLog(AuditAction.CREATE, AuditEntityType.TICKET, 2L, AuditActor.USER, Instant.parse("2026-03-01T11:00:00Z"));

		mockMvc.perform(get("/audit-logs").param("entityType", "TICKET"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].entityType").value("TICKET"));
	}

	@Test
	void getAuditLogsFiltersByAction() throws Exception {
		saveAuditLog(AuditAction.CREATE, AuditEntityType.USER, 1L, AuditActor.USER, Instant.parse("2026-03-01T10:00:00Z"));
		saveAuditLog(AuditAction.UPDATE, AuditEntityType.USER, 1L, AuditActor.USER, Instant.parse("2026-03-01T11:00:00Z"));

		mockMvc.perform(get("/audit-logs").param("action", "CREATE"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].action").value("CREATE"));
	}

	@Test
	void getAuditLogsFiltersByActor() throws Exception {
		saveAuditLog(AuditAction.CREATE, AuditEntityType.USER, 1L, AuditActor.USER, Instant.parse("2026-03-01T10:00:00Z"));
		saveAuditLog(AuditAction.AUTO_ESCALATE, AuditEntityType.TICKET, 2L, AuditActor.SYSTEM, Instant.parse("2026-03-01T11:00:00Z"));

		mockMvc.perform(get("/audit-logs").param("actor", "USER"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].actor").value("USER"));
	}

	@Test
	void getAuditLogsFiltersByEntityId() throws Exception {
		saveAuditLog(AuditAction.CREATE, AuditEntityType.USER, 1L, AuditActor.USER, Instant.parse("2026-03-01T10:00:00Z"));
		saveAuditLog(AuditAction.CREATE, AuditEntityType.USER, 2L, AuditActor.USER, Instant.parse("2026-03-01T11:00:00Z"));

		mockMvc.perform(get("/audit-logs").param("entityId", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].entityId").value(2));
	}

	@Test
	void invalidEnumFilterReturnsInformativeBadRequest() throws Exception {
		mockMvc.perform(get("/audit-logs").param("entityType", "INVALID"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("entityType must be one of:")))
			.andExpect(jsonPath("$.message").value(containsString("TICKET")));
	}

	private AuditLog singleAuditLog() {
		List<AuditLog> auditLogs = auditLogRepository.findAll();
		Assertions.assertThat(auditLogs).hasSize(1);
		return auditLogs.getFirst();
	}

	private AuditLog singleAuditLog(AuditAction action, AuditEntityType entityType) {
		List<AuditLog> auditLogs = auditLogRepository.findAll()
			.stream()
			.filter(log -> log.getAction() == action && log.getEntityType() == entityType)
			.toList();
		Assertions.assertThat(auditLogs).hasSize(1);
		return auditLogs.getFirst();
	}

	private AuditLog saveAuditLog(
		AuditAction action,
		AuditEntityType entityType,
		Long entityId,
		AuditActor actor,
		Instant timestamp
	) {
		AuditLog auditLog = new AuditLog();
		auditLog.setAction(action);
		auditLog.setEntityType(entityType);
		auditLog.setEntityId(entityId);
		auditLog.setActor(actor);
		auditLog.setTimestamp(timestamp);
		return auditLogRepository.save(auditLog);
	}

	private User saveUser(String username, String email) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName("Test User");
		user.setRole(UserRole.DEVELOPER);
		return userRepository.save(user);
	}

	private Project saveProject() {
		User owner = saveUser("owner" + System.nanoTime(), "owner" + System.nanoTime() + "@example.com");
		Project project = new Project();
		project.setName("Sample Project");
		project.setDescription("A sample project");
		project.setOwnerId(owner.getId());
		return projectRepository.save(project);
	}

	private Ticket saveTicket() {
		Project project = saveProject();
		Ticket ticket = new Ticket();
		ticket.setTitle("Fix login bug");
		ticket.setDescription("Login fails for valid users");
		ticket.setStatus(TicketStatus.TODO);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(project.getId());
		return ticketRepository.save(ticket);
	}
}
