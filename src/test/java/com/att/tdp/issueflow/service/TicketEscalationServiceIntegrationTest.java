package com.att.tdp.issueflow.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TicketEscalationServiceIntegrationTest {

	private static final Instant PAST_DUE_DATE = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant FUTURE_DUE_DATE = Instant.parse("2099-01-01T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TicketEscalationService ticketEscalationService;

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
	void creatingTicketWithDueDateReturnsDueDateAndIsOverdueFalse() throws Exception {
		Project project = saveProject();
		CreateTicketRequest request = new CreateTicketRequest(
			"Due ticket",
			"Description",
			TicketStatus.TODO,
			TicketPriority.LOW,
			TicketType.BUG,
			project.getId(),
			null,
			FUTURE_DUE_DATE
		);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dueDate").value("2099-01-01T00:00:00Z"))
			.andExpect(jsonPath("$.isOverdue").value(false));
	}

	@Test
	void updatingDueDateOnNonDoneTicketSucceeds() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), TicketStatus.IN_PROGRESS, TicketPriority.LOW, null, null, false);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, null, null, null, FUTURE_DUE_DATE);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dueDate").value("2099-01-01T00:00:00Z"));
	}

	@Test
	void updatingDueDateOnDoneTicketFails() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), TicketStatus.DONE, TicketPriority.LOW, null, null, false);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, null, null, null, FUTURE_DUE_DATE);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("DONE")));
	}

	@Test
	void overdueLowTicketEscalatesToMedium() {
		Ticket ticket = savePastDueTicket(TicketPriority.LOW);

		int changedCount = ticketEscalationService.runEscalationCycle();

		Ticket escalatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(changedCount).isEqualTo(1);
		Assertions.assertThat(escalatedTicket.getPriority()).isEqualTo(TicketPriority.MEDIUM);
		Assertions.assertThat(escalatedTicket.isOverdue()).isFalse();
	}

	@Test
	void overdueMediumTicketEscalatesToHigh() {
		Ticket ticket = savePastDueTicket(TicketPriority.MEDIUM);

		ticketEscalationService.runEscalationCycle();

		Ticket escalatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(escalatedTicket.getPriority()).isEqualTo(TicketPriority.HIGH);
		Assertions.assertThat(escalatedTicket.isOverdue()).isFalse();
	}

	@Test
	void overdueHighTicketEscalatesToCriticalAndIsOverdueTrue() {
		Ticket ticket = savePastDueTicket(TicketPriority.HIGH);

		ticketEscalationService.runEscalationCycle();

		Ticket escalatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(escalatedTicket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
		Assertions.assertThat(escalatedTicket.isOverdue()).isTrue();
	}

	@Test
	void overdueCriticalTicketRemainsCriticalAndIsOverdueTrue() {
		Ticket ticket = savePastDueTicket(TicketPriority.CRITICAL);

		ticketEscalationService.runEscalationCycle();

		Ticket escalatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(escalatedTicket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
		Assertions.assertThat(escalatedTicket.isOverdue()).isTrue();
	}

	@Test
	void nonOverdueTicketDoesNotEscalate() {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), TicketStatus.TODO, TicketPriority.LOW, FUTURE_DUE_DATE, null, false);

		int changedCount = ticketEscalationService.runEscalationCycle();

		Ticket unchangedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(changedCount).isZero();
		Assertions.assertThat(unchangedTicket.getPriority()).isEqualTo(TicketPriority.LOW);
		Assertions.assertThat(unchangedTicket.isOverdue()).isFalse();
	}

	@Test
	void ticketWithoutDueDateDoesNotEscalate() {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), TicketStatus.TODO, TicketPriority.LOW, null, null, false);

		int changedCount = ticketEscalationService.runEscalationCycle();

		Ticket unchangedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(changedCount).isZero();
		Assertions.assertThat(unchangedTicket.getPriority()).isEqualTo(TicketPriority.LOW);
	}

	@Test
	void doneTicketDoesNotEscalate() {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), TicketStatus.DONE, TicketPriority.LOW, PAST_DUE_DATE, null, false);

		int changedCount = ticketEscalationService.runEscalationCycle();

		Ticket unchangedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(changedCount).isZero();
		Assertions.assertThat(unchangedTicket.getPriority()).isEqualTo(TicketPriority.LOW);
	}

	@Test
	void softDeletedTicketDoesNotEscalate() {
		Project project = saveProject();
		Ticket ticket = saveTicket(
			project.getId(),
			TicketStatus.TODO,
			TicketPriority.LOW,
			PAST_DUE_DATE,
			Instant.now(),
			false
		);

		int changedCount = ticketEscalationService.runEscalationCycle();

		Ticket unchangedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(changedCount).isZero();
		Assertions.assertThat(unchangedTicket.getPriority()).isEqualTo(TicketPriority.LOW);
	}

	@Test
	void escalationDoesNotChangeStatus() {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), TicketStatus.IN_REVIEW, TicketPriority.HIGH, PAST_DUE_DATE, null, false);

		ticketEscalationService.runEscalationCycle();

		Ticket escalatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(escalatedTicket.getStatus()).isEqualTo(TicketStatus.IN_REVIEW);
		Assertions.assertThat(escalatedTicket.getPriority()).isEqualTo(TicketPriority.CRITICAL);
	}

	@Test
	void manualPriorityChangeClearsIsOverdue() throws Exception {
		Project project = saveProject();
		Ticket ticket = saveTicket(project.getId(), TicketStatus.TODO, TicketPriority.CRITICAL, PAST_DUE_DATE, null, true);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, null, TicketPriority.HIGH, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		Ticket updatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(updatedTicket.getPriority()).isEqualTo(TicketPriority.HIGH);
		Assertions.assertThat(updatedTicket.isOverdue()).isFalse();
	}

	@Test
	void escalationCreatesSystemAuditLogWhenTicketChanges() {
		Ticket ticket = savePastDueTicket(TicketPriority.HIGH);

		ticketEscalationService.runEscalationCycle();

		var auditLog = auditLogRepository.findAll()
			.stream()
			.filter(log -> log.getAction() == AuditAction.AUTO_ESCALATE)
			.findFirst()
			.orElseThrow();
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(ticket.getId());
		Assertions.assertThat(auditLog.getActor()).isEqualTo(AuditActor.SYSTEM);
		Assertions.assertThat(auditLog.getPerformedBy()).isNull();
	}

	@Test
	void criticalAlreadyOverdueTicketDoesNotCreateDuplicateAuditLog() {
		Project project = saveProject();
		saveTicket(project.getId(), TicketStatus.TODO, TicketPriority.CRITICAL, PAST_DUE_DATE, null, true);

		int changedCount = ticketEscalationService.runEscalationCycle();

		Assertions.assertThat(changedCount).isZero();
		Assertions.assertThat(countAuditLogs(AuditAction.AUTO_ESCALATE)).isZero();
	}

	private Ticket savePastDueTicket(TicketPriority priority) {
		Project project = saveProject();
		return saveTicket(project.getId(), TicketStatus.TODO, priority, PAST_DUE_DATE, null, false);
	}

	private long countAuditLogs(AuditAction action) {
		return auditLogRepository.findAll()
			.stream()
			.filter(log -> log.getAction() == action)
			.count();
	}

	private User saveUser(String username, UserRole role) {
		User user = new User();
		user.setUsername(username + System.nanoTime());
		user.setEmail(username + System.nanoTime() + "@example.com");
		user.setFullName("Test User");
		user.setRole(role);
		return userRepository.save(user);
	}

	private Project saveProject() {
		User owner = saveUser("owner", UserRole.ADMIN);
		Project project = new Project();
		project.setName("Sample Project");
		project.setDescription("A sample project");
		project.setOwnerId(owner.getId());
		return projectRepository.save(project);
	}

	private Ticket saveTicket(
		Long projectId,
		TicketStatus status,
		TicketPriority priority,
		Instant dueDate,
		Instant deletedAt,
		boolean overdue
	) {
		Ticket ticket = new Ticket();
		ticket.setTitle("Ticket " + System.nanoTime());
		ticket.setDescription("Description");
		ticket.setStatus(status);
		ticket.setPriority(priority);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		ticket.setDueDate(dueDate);
		ticket.setDeletedAt(deletedAt);
		ticket.setOverdue(overdue);
		return ticketRepository.save(ticket);
	}
}
