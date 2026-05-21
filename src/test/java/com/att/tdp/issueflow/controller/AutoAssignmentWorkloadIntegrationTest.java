package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class AutoAssignmentWorkloadIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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
	void creatingTicketWithoutAssigneeAutoAssignsOnlyDeveloper() throws Exception {
		Project project = saveProject();
		User developer = saveUser("dev", UserRole.DEVELOPER);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), null))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(developer.getId()));
	}

	@Test
	void creatingTicketWithoutAssigneeIgnoresAdminUsers() throws Exception {
		Project project = saveProject();
		User admin = saveUser("admin", UserRole.ADMIN);
		User developer = saveUser("dev", UserRole.DEVELOPER);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), null))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(developer.getId()));

		Assertions.assertThat(ticketRepository.findAll().getFirst().getAssigneeId()).isNotEqualTo(admin.getId());
	}

	@Test
	void creatingTicketWithoutAssigneeChoosesLeastLoadedDeveloper() throws Exception {
		Project project = saveProject();
		User loadedDeveloper = saveUser("loaded", UserRole.DEVELOPER);
		User leastLoadedDeveloper = saveUser("least", UserRole.DEVELOPER);
		saveTicket(project.getId(), loadedDeveloper.getId(), TicketStatus.TODO, null);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), null))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(leastLoadedDeveloper.getId()));
	}

	@Test
	void tieIsBrokenBySmallestUserId() throws Exception {
		Project project = saveProject();
		User olderDeveloper = saveUser("older", UserRole.DEVELOPER);
		saveUser("newer", UserRole.DEVELOPER);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), null))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(olderDeveloper.getId()));
	}

	@Test
	void ifNoDeveloperExistsTicketIsCreatedUnassigned() throws Exception {
		Project project = saveProject();
		saveUser("admin", UserRole.ADMIN);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), null))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(nullValue()));
	}

	@Test
	void creatingTicketWithExplicitAssigneeDoesNotAutoAssign() throws Exception {
		Project project = saveProject();
		User explicitAssignee = saveUser("explicit", UserRole.DEVELOPER);
		User olderDeveloper = saveUser("older", UserRole.DEVELOPER);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), explicitAssignee.getId()))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assigneeId").value(explicitAssignee.getId()));

		Assertions.assertThat(explicitAssignee.getId()).isNotEqualTo(olderDeveloper.getId());
		Assertions.assertThat(countAuditLogs(AuditAction.AUTO_ASSIGN)).isZero();
	}

	@Test
	void patchTicketWithAssigneeOverrideWorksAndDoesNotTriggerAutoAssign() throws Exception {
		Project project = saveProject();
		User developer = saveUser("dev", UserRole.DEVELOPER);
		Ticket ticket = saveTicket(project.getId(), null, TicketStatus.TODO, null);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, null, null, developer.getId());

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		Ticket updatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(updatedTicket.getAssigneeId()).isEqualTo(developer.getId());
		Assertions.assertThat(countAuditLogs(AuditAction.AUTO_ASSIGN)).isZero();
	}

	@Test
	void autoAssignmentCreatesSystemAuditLog() throws Exception {
		Project project = saveProject();
		saveUser("dev", UserRole.DEVELOPER);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), null))))
			.andExpect(status().isOk());

		var autoAssignLog = auditLogRepository.findAll()
			.stream()
			.filter(log -> log.getAction() == AuditAction.AUTO_ASSIGN)
			.findFirst()
			.orElseThrow();
		Ticket ticket = ticketRepository.findAll().getFirst();
		Assertions.assertThat(autoAssignLog.getEntityType()).isEqualTo(AuditEntityType.TICKET);
		Assertions.assertThat(autoAssignLog.getEntityId()).isEqualTo(ticket.getId());
		Assertions.assertThat(autoAssignLog.getActor()).isEqualTo(AuditActor.SYSTEM);
		Assertions.assertThat(autoAssignLog.getPerformedBy()).isNull();
	}

	@Test
	void noAutoAssignAuditLogIsCreatedWhenNoDeveloperExists() throws Exception {
		Project project = saveProject();
		saveUser("admin", UserRole.ADMIN);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(createTicketRequest(project.getId(), null))))
			.andExpect(status().isOk());

		Assertions.assertThat(countAuditLogs(AuditAction.AUTO_ASSIGN)).isZero();
	}

	@Test
	void workloadReturnsAllDevelopersAndExcludesAdmins() throws Exception {
		Project project = saveProject();
		User developerOne = saveUser("dev1", UserRole.DEVELOPER);
		User developerTwo = saveUser("dev2", UserRole.DEVELOPER);
		saveUser("admin", UserRole.ADMIN);

		mockMvc.perform(get("/projects/{projectId}/workload", project.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].userId").value(developerOne.getId()))
			.andExpect(jsonPath("$[0].username").value(developerOne.getUsername()))
			.andExpect(jsonPath("$[0].openTicketCount").value(0))
			.andExpect(jsonPath("$[1].userId").value(developerTwo.getId()))
			.andExpect(jsonPath("$[1].username").value(developerTwo.getUsername()))
			.andExpect(jsonPath("$[1].openTicketCount").value(0));
	}

	@Test
	void workloadCountsOnlyNonDoneTicketsAndIgnoresSoftDeletedTickets() throws Exception {
		Project project = saveProject();
		User developer = saveUser("dev", UserRole.DEVELOPER);
		saveTicket(project.getId(), developer.getId(), TicketStatus.TODO, null);
		saveTicket(project.getId(), developer.getId(), TicketStatus.IN_PROGRESS, null);
		saveTicket(project.getId(), developer.getId(), TicketStatus.DONE, null);
		saveTicket(project.getId(), developer.getId(), TicketStatus.TODO, Instant.now());

		mockMvc.perform(get("/projects/{projectId}/workload", project.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].openTicketCount").value(2));
	}

	@Test
	void workloadIsSortedByOpenTicketCountThenUserId() throws Exception {
		Project project = saveProject();
		User firstDeveloper = saveUser("first", UserRole.DEVELOPER);
		User secondDeveloper = saveUser("second", UserRole.DEVELOPER);
		User thirdDeveloper = saveUser("third", UserRole.DEVELOPER);
		saveTicket(project.getId(), firstDeveloper.getId(), TicketStatus.TODO, null);
		saveTicket(project.getId(), thirdDeveloper.getId(), TicketStatus.TODO, null);
		saveTicket(project.getId(), thirdDeveloper.getId(), TicketStatus.IN_REVIEW, null);

		mockMvc.perform(get("/projects/{projectId}/workload", project.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].userId").value(secondDeveloper.getId()))
			.andExpect(jsonPath("$[0].openTicketCount").value(0))
			.andExpect(jsonPath("$[1].userId").value(firstDeveloper.getId()))
			.andExpect(jsonPath("$[1].openTicketCount").value(1))
			.andExpect(jsonPath("$[2].userId").value(thirdDeveloper.getId()))
			.andExpect(jsonPath("$[2].openTicketCount").value(2));
	}

	@Test
	void workloadFailsWhenProjectDoesNotExist() throws Exception {
		mockMvc.perform(get("/projects/{projectId}/workload", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found: 999"));
	}

	private CreateTicketRequest createTicketRequest(Long projectId, Long assigneeId) {
		return new CreateTicketRequest(
			"Fix login bug",
			"Login fails for valid users",
			TicketStatus.TODO,
			TicketPriority.HIGH,
			TicketType.BUG,
			projectId,
			assigneeId,
			null
		);
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

	private Ticket saveTicket(Long projectId, Long assigneeId, TicketStatus status, Instant deletedAt) {
		Ticket ticket = new Ticket();
		ticket.setTitle("Ticket " + System.nanoTime());
		ticket.setDescription("Description");
		ticket.setStatus(status);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		ticket.setAssigneeId(assigneeId);
		ticket.setDeletedAt(deletedAt);
		return ticketRepository.save(ticket);
	}
}
