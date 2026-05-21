package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.AddDependencyRequest;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
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
class TicketDependencyControllerIntegrationTest {

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
	private TicketDependencyRepository ticketDependencyRepository;

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
		ticketDependencyRepository.deleteAll();
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
		auditLogRepository.deleteAll();
	}

	@Test
	void addDependencySuccessfully() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.IN_PROGRESS, null);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddDependencyRequest(blocker.getId()))))
			.andExpect(status().isOk());

		Assertions.assertThat(
			ticketDependencyRepository.existsByTicketIdAndBlockedByTicketId(ticket.getId(), blocker.getId())
		).isTrue();
	}

	@Test
	void addDependencyFailsWhenTicketDoesNotExist() throws Exception {
		Project project = saveProject();
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.TODO, null);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", 999L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddDependencyRequest(blocker.getId()))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found: 999"));
	}

	@Test
	void addDependencyFailsWhenBlockedByDoesNotExist() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddDependencyRequest(999L))))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Blocker ticket not found: 999"));
	}

	@Test
	void addDependencyFailsWhenBlockedByIsMissing() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.fieldErrors.blockedBy").value("blockedBy is required"));
	}

	@Test
	void addDependencyFailsWhenTicketsBelongToDifferentProjects() throws Exception {
		Ticket ticket = saveTicket(saveProject().getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(saveProject().getId(), "Blocking ticket", TicketStatus.TODO, null);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddDependencyRequest(blocker.getId()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Tickets must belong to the same project."));
	}

	@Test
	void addDependencyFailsForSelfDependency() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddDependencyRequest(ticket.getId()))))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Ticket cannot depend on itself."));
	}

	@Test
	void addingSameDependencyTwiceIsIdempotent() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.TODO, null);
		AddDependencyRequest request = new AddDependencyRequest(blocker.getId());

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());
		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		Assertions.assertThat(ticketDependencyRepository.findAllByTicketId(ticket.getId())).hasSize(1);
	}

	@Test
	void listDependenciesReturnsBlockerTickets() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.IN_PROGRESS, null);
		saveDependency(ticket.getId(), blocker.getId());

		mockMvc.perform(get("/tickets/{ticketId}/dependencies", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(blocker.getId()))
			.andExpect(jsonPath("$[0].title").value("Blocking ticket"))
			.andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
	}

	@Test
	void listDependenciesExcludesSoftDeletedBlockerTickets() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.IN_PROGRESS, Instant.now());
		saveDependency(ticket.getId(), blocker.getId());

		mockMvc.perform(get("/tickets/{ticketId}/dependencies", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void removeDependencySuccessfully() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.TODO, null);
		saveDependency(ticket.getId(), blocker.getId());

		mockMvc.perform(delete(
				"/tickets/{ticketId}/dependencies/{blockerId}",
				ticket.getId(),
				blocker.getId()
			))
			.andExpect(status().isOk());

		Assertions.assertThat(
			ticketDependencyRepository.existsByTicketIdAndBlockedByTicketId(ticket.getId(), blocker.getId())
		).isFalse();
	}

	@Test
	void removingMissingDependencyIsIdempotent() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.TODO, null);

		mockMvc.perform(delete(
				"/tickets/{ticketId}/dependencies/{blockerId}",
				ticket.getId(),
				blocker.getId()
			))
			.andExpect(status().isOk());

		Assertions.assertThat(auditLogRepository.findAll()).isEmpty();
	}

	@Test
	void addDependencyCreatesAuditLog() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.TODO, null);

		mockMvc.perform(post("/tickets/{ticketId}/dependencies", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new AddDependencyRequest(blocker.getId()))))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.ADD_DEPENDENCY);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.DEPENDENCY);
		Assertions.assertThat(auditLog.getEntityId()).isNotNull();
	}

	@Test
	void removeDependencyCreatesAuditLog() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.TODO, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.TODO, null);
		TicketDependency dependency = saveDependency(ticket.getId(), blocker.getId());

		mockMvc.perform(delete(
				"/tickets/{ticketId}/dependencies/{blockerId}",
				ticket.getId(),
				blocker.getId()
			))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.REMOVE_DEPENDENCY);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.DEPENDENCY);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(dependency.getId());
	}

	@Test
	void ticketWithUnresolvedBlockerCannotTransitionToDone() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.IN_REVIEW, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.IN_PROGRESS, null);
		saveDependency(ticket.getId(), blocker.getId());
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, TicketStatus.DONE, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				"Ticket cannot be marked DONE because it has unresolved blockers."
			));
	}

	@Test
	void ticketCanTransitionToDoneWhenAllBlockersAreDone() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), "Blocked ticket", TicketStatus.IN_REVIEW, null);
		Ticket blocker = saveTicket(project.getId(), "Blocking ticket", TicketStatus.DONE, null);
		saveDependency(ticket.getId(), blocker.getId());
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, TicketStatus.DONE, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		Ticket updatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(updatedTicket.getStatus()).isEqualTo(TicketStatus.DONE);
	}

	private AuditLog singleAuditLog() {
		List<AuditLog> auditLogs = auditLogRepository.findAll();
		Assertions.assertThat(auditLogs).hasSize(1);
		return auditLogs.getFirst();
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

	private Ticket saveTicket(Long projectId, String title, TicketStatus status, Instant deletedAt) {
		Ticket ticket = new Ticket();
		ticket.setTitle(title);
		ticket.setDescription("Ticket description");
		ticket.setStatus(status);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		ticket.setDeletedAt(deletedAt);
		return ticketRepository.save(ticket);
	}

	private TicketDependency saveDependency(Long ticketId, Long blockerId) {
		TicketDependency dependency = new TicketDependency();
		dependency.setTicketId(ticketId);
		dependency.setBlockedByTicketId(blockerId);
		return ticketDependencyRepository.save(dependency);
	}
}
