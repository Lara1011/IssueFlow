package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class SoftDeleteControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

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
	void getDeletedProjectsReturnsOnlyDeletedProjects() throws Exception {
		Project deletedProject = saveProject("Deleted Project", Instant.parse("2026-03-02T10:00:00Z"));
		saveProject("Active Project", null);

		mockMvc.perform(get("/projects/deleted"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(deletedProject.getId()))
			.andExpect(jsonPath("$[0].name").value("Deleted Project"));
	}

	@Test
	void restoreDeletedProject() throws Exception {
		Project project = saveProject("Deleted Project", Instant.now());

		mockMvc.perform(post("/projects/{projectId}/restore", project.getId()))
			.andExpect(status().isOk());

		Project restoredProject = projectRepository.findById(project.getId()).orElseThrow();
		Assertions.assertThat(restoredProject.getDeletedAt()).isNull();
	}

	@Test
	void restoredProjectAppearsInStandardProjectList() throws Exception {
		Project project = saveProject("Deleted Project", Instant.now());

		mockMvc.perform(post("/projects/{projectId}/restore", project.getId()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(project.getId()));
	}

	@Test
	void restoringNonDeletedProjectIsIdempotent() throws Exception {
		Project project = saveProject("Active Project", null);

		mockMvc.perform(post("/projects/{projectId}/restore", project.getId()))
			.andExpect(status().isOk());

		Assertions.assertThat(auditLogRepository.findAll()).isEmpty();
	}

	@Test
	void getDeletedTicketsReturnsOnlyDeletedTicketsForProject() throws Exception {
		Project project = saveProject("Project", null);
		Project otherProject = saveProject("Other Project", null);
		Ticket deletedTicket = saveTicket(project.getId(), "Deleted Ticket", Instant.parse("2026-03-02T10:00:00Z"));
		saveTicket(project.getId(), "Active Ticket", null);
		saveTicket(otherProject.getId(), "Other Deleted Ticket", Instant.parse("2026-03-03T10:00:00Z"));

		mockMvc.perform(get("/tickets/deleted").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(deletedTicket.getId()))
			.andExpect(jsonPath("$[0].title").value("Deleted Ticket"));
	}

	@Test
	void getDeletedTicketsWithoutProjectIdReturnsInformativeBadRequest() throws Exception {
		mockMvc.perform(get("/tickets/deleted"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("projectId is required"));
	}

	@Test
	void restoreDeletedTicket() throws Exception {
		Project project = saveProject("Project", null);
		Ticket ticket = saveTicket(project.getId(), "Deleted Ticket", Instant.now());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticket.getId()))
			.andExpect(status().isOk());

		Ticket restoredTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(restoredTicket.getDeletedAt()).isNull();
	}

	@Test
	void restoredTicketAppearsInStandardTicketList() throws Exception {
		Project project = saveProject("Project", null);
		Ticket ticket = saveTicket(project.getId(), "Deleted Ticket", Instant.now());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticket.getId()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(ticket.getId()));
	}

	@Test
	void restoringNonDeletedTicketIsIdempotent() throws Exception {
		Project project = saveProject("Project", null);
		Ticket ticket = saveTicket(project.getId(), "Active Ticket", null);

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticket.getId()))
			.andExpect(status().isOk());

		Assertions.assertThat(auditLogRepository.findAll()).isEmpty();
	}

	@Test
	void restoringTicketWhoseProjectIsDeletedReturnsInformativeBadRequest() throws Exception {
		Project project = saveProject("Deleted Project", Instant.now());
		Ticket ticket = saveTicket(project.getId(), "Deleted Ticket", Instant.now());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticket.getId()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Cannot restore ticket because its project is deleted."));
	}

	@Test
	void restoreProjectCreatesAuditLog() throws Exception {
		Project project = saveProject("Deleted Project", Instant.now());

		mockMvc.perform(post("/projects/{projectId}/restore", project.getId()))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.RESTORE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.PROJECT);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(project.getId());
	}

	@Test
	void restoreTicketCreatesAuditLog() throws Exception {
		Project project = saveProject("Project", null);
		Ticket ticket = saveTicket(project.getId(), "Deleted Ticket", Instant.now());

		mockMvc.perform(post("/tickets/{ticketId}/restore", ticket.getId()))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.RESTORE);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.TICKET);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(ticket.getId());
	}

	@Test
	void standardEndpointsContinueHidingSoftDeletedRecords() throws Exception {
		Project deletedProject = saveProject("Deleted Project", Instant.now());
		Project activeProject = saveProject("Active Project", null);
		Ticket deletedTicket = saveTicket(activeProject.getId(), "Deleted Ticket", Instant.now());

		mockMvc.perform(get("/projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(activeProject.getId()));

		mockMvc.perform(get("/projects/{projectId}", deletedProject.getId()))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/tickets").param("projectId", activeProject.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));

		mockMvc.perform(get("/tickets/{ticketId}", deletedTicket.getId()))
			.andExpect(status().isNotFound());
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

	private Project saveProject(String name, Instant deletedAt) {
		User owner = saveUser("owner" + System.nanoTime(), "owner" + System.nanoTime() + "@example.com");
		Project project = new Project();
		project.setName(name);
		project.setDescription("A sample project");
		project.setOwnerId(owner.getId());
		project.setDeletedAt(deletedAt);
		return projectRepository.save(project);
	}

	private Ticket saveTicket(Long projectId, String title, Instant deletedAt) {
		Ticket ticket = new Ticket();
		ticket.setTitle(title);
		ticket.setDescription("Ticket description");
		ticket.setStatus(TicketStatus.TODO);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		ticket.setDeletedAt(deletedAt);
		return ticketRepository.save(ticket);
	}
}
