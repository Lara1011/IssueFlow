package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
import java.time.Instant;
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
class TicketControllerIntegrationTest {

	private static final Instant DUE_DATE = Instant.parse("2026-04-01T00:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TicketRepository ticketRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createTicketSuccessfully() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		User assignee = saveUser("dev", "dev@example.com", "Developer User", UserRole.DEVELOPER);
		Project project = saveProject("Sample Project", owner.getId(), null);
		CreateTicketRequest request = new CreateTicketRequest(
			"Fix login bug",
			"Login fails for valid users",
			TicketStatus.TODO,
			TicketPriority.HIGH,
			TicketType.BUG,
			project.getId(),
			assignee.getId(),
			DUE_DATE
		);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.title").value("Fix login bug"))
			.andExpect(jsonPath("$.description").value("Login fails for valid users"))
			.andExpect(jsonPath("$.status").value("TODO"))
			.andExpect(jsonPath("$.priority").value("HIGH"))
			.andExpect(jsonPath("$.type").value("BUG"))
			.andExpect(jsonPath("$.projectId").value(project.getId()))
			.andExpect(jsonPath("$.assigneeId").value(assignee.getId()))
			.andExpect(jsonPath("$.dueDate").value("2026-04-01T00:00:00Z"))
			.andExpect(jsonPath("$.isOverdue").value(false));
	}

	@Test
	void createTicketWithNoAssigneeSuccessfully() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		CreateTicketRequest request = new CreateTicketRequest(
			"Fix login bug",
			"Login fails for valid users",
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
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Fix login bug"))
			.andExpect(jsonPath("$.assigneeId").doesNotExist())
			.andExpect(jsonPath("$.dueDate").doesNotExist())
			.andExpect(jsonPath("$.isOverdue").value(false));
	}

	@Test
	void createTicketFailsWhenProjectDoesNotExist() throws Exception {
		CreateTicketRequest request = new CreateTicketRequest(
			"Fix login bug",
			"Login fails for valid users",
			TicketStatus.TODO,
			TicketPriority.HIGH,
			TicketType.BUG,
			999L,
			null,
			null
		);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found: 999"));
	}

	@Test
	void createTicketFailsWhenAssigneeDoesNotExist() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		CreateTicketRequest request = new CreateTicketRequest(
			"Fix login bug",
			"Login fails for valid users",
			TicketStatus.TODO,
			TicketPriority.HIGH,
			TicketType.BUG,
			project.getId(),
			999L,
			null
		);

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Assignee not found: 999"));
	}

	@Test
	void getTicketsByProjectReturnsOnlyNonDeletedTickets() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		saveTicket("Visible Ticket", project.getId(), null, null);
		saveTicket("Deleted Ticket", project.getId(), null, Instant.now());

		mockMvc.perform(get("/tickets").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].title").value("Visible Ticket"))
			.andExpect(jsonPath("$[*].title", not(contains("Deleted Ticket"))));
	}

	@Test
	void getTicketById() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		User assignee = saveUser("dev", "dev@example.com", "Developer User", UserRole.DEVELOPER);
		Project project = saveProject("Sample Project", owner.getId(), null);
		Ticket ticket = saveTicket("Fix login bug", project.getId(), assignee.getId(), null);

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ticket.getId()))
			.andExpect(jsonPath("$.title").value("Fix login bug"))
			.andExpect(jsonPath("$.status").value("TODO"))
			.andExpect(jsonPath("$.priority").value("HIGH"))
			.andExpect(jsonPath("$.type").value("BUG"))
			.andExpect(jsonPath("$.projectId").value(project.getId()))
			.andExpect(jsonPath("$.assigneeId").value(assignee.getId()))
			.andExpect(jsonPath("$.isOverdue").value(false));
	}

	@Test
	void updateTicketBasicFields() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		User oldAssignee = saveUser("dev1", "dev1@example.com", "Developer One", UserRole.DEVELOPER);
		User newAssignee = saveUser("dev2", "dev2@example.com", "Developer Two", UserRole.DEVELOPER);
		Project project = saveProject("Sample Project", owner.getId(), null);
		Ticket ticket = saveTicket("Fix login bug", project.getId(), oldAssignee.getId(), null);
		UpdateTicketRequest request = new UpdateTicketRequest(
			"Updated title",
			"Updated description",
			TicketStatus.IN_PROGRESS,
			TicketPriority.CRITICAL,
			newAssignee.getId()
		);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Updated title"))
			.andExpect(jsonPath("$.description").value("Updated description"))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.priority").value("CRITICAL"))
			.andExpect(jsonPath("$.type").value("BUG"))
			.andExpect(jsonPath("$.projectId").value(project.getId()))
			.andExpect(jsonPath("$.assigneeId").value(newAssignee.getId()))
			.andExpect(jsonPath("$.dueDate").value("2026-04-01T00:00:00Z"));
	}

	@Test
	void cannotUpdateAnyFieldWhenTicketIsDone() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		Ticket ticket = saveTicket("Done ticket", project.getId(), null, null, TicketStatus.DONE);
		UpdateTicketRequest request = new UpdateTicketRequest(
			"Updated title",
			null,
			TicketStatus.DONE,
			TicketPriority.CRITICAL,
			null
		);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("DONE")))
			.andExpect(jsonPath("$.message").value("Ticket cannot be updated because it is already DONE."));

		Ticket unchangedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(unchangedTicket.getTitle()).isEqualTo("Done ticket");
		Assertions.assertThat(unchangedTicket.getPriority()).isEqualTo(TicketPriority.HIGH);
		Assertions.assertThat(unchangedTicket.getStatus()).isEqualTo(TicketStatus.DONE);
	}

	@Test
	void cannotChangeStatusFromInReviewToTodo() throws Exception {
		Ticket ticket = saveTicketWithStatus(TicketStatus.IN_REVIEW);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, TicketStatus.TODO, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("IN_REVIEW")))
			.andExpect(jsonPath("$.message", containsString("TODO")))
			.andExpect(jsonPath("$.message").value(
				"Invalid ticket status transition from IN_REVIEW to TODO. Allowed next status is DONE."
			));
	}

	@Test
	void cannotChangeStatusFromInReviewToInProgress() throws Exception {
		Ticket ticket = saveTicketWithStatus(TicketStatus.IN_REVIEW);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, TicketStatus.IN_PROGRESS, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				"Invalid ticket status transition from IN_REVIEW to IN_PROGRESS. Allowed next status is DONE."
			));
	}

	@Test
	void cannotChangeStatusFromInProgressToTodo() throws Exception {
		Ticket ticket = saveTicketWithStatus(TicketStatus.IN_PROGRESS);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, TicketStatus.TODO, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(
				"Invalid ticket status transition from IN_PROGRESS to TODO. Allowed next status is IN_REVIEW."
			));
	}

	@Test
	void canChangeStatusFromTodoToInProgress() throws Exception {
		assertStatusTransitionAllowed(TicketStatus.TODO, TicketStatus.IN_PROGRESS);
	}

	@Test
	void cannotChangeStatusFromTodoToInReview() throws Exception {
		assertStatusTransitionRejected(
			TicketStatus.TODO,
			TicketStatus.IN_REVIEW,
			"Invalid ticket status transition from TODO to IN_REVIEW. Allowed next status is IN_PROGRESS."
		);
	}

	@Test
	void cannotChangeStatusFromTodoToDone() throws Exception {
		assertStatusTransitionRejected(
			TicketStatus.TODO,
			TicketStatus.DONE,
			"Invalid ticket status transition from TODO to DONE. Allowed next status is IN_PROGRESS."
		);
	}

	@Test
	void canChangeStatusFromInProgressToInReview() throws Exception {
		assertStatusTransitionAllowed(TicketStatus.IN_PROGRESS, TicketStatus.IN_REVIEW);
	}

	@Test
	void cannotChangeStatusFromInProgressToDone() throws Exception {
		assertStatusTransitionRejected(
			TicketStatus.IN_PROGRESS,
			TicketStatus.DONE,
			"Invalid ticket status transition from IN_PROGRESS to DONE. Allowed next status is IN_REVIEW."
		);
	}

	@Test
	void canChangeStatusFromInReviewToDone() throws Exception {
		assertStatusTransitionAllowed(TicketStatus.IN_REVIEW, TicketStatus.DONE);
	}

	@Test
	void sameStatusUpdateIsAllowedWhenTicketIsNotDone() throws Exception {
		Ticket ticket = saveTicketWithStatus(TicketStatus.IN_PROGRESS);
		UpdateTicketRequest request = new UpdateTicketRequest(
			"Same status title change",
			null,
			TicketStatus.IN_PROGRESS,
			null,
			null
		);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Same status title change"))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"));
	}

	@Test
	void patchWithProjectIdOrTypeDoesNotChangeThoseFields() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project originalProject = saveProject("Original Project", owner.getId(), null);
		Project ignoredProject = saveProject("Ignored Project", owner.getId(), null);
		Ticket ticket = saveTicket("Fix login bug", originalProject.getId(), null, null);
		String request = """
			{
			  "title": "Updated title",
			  "projectId": %d,
			  "type": "FEATURE"
			}
			""".formatted(ignoredProject.getId());

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Updated title"))
			.andExpect(jsonPath("$.projectId").value(originalProject.getId()))
			.andExpect(jsonPath("$.type").value("BUG"))
			.andExpect(jsonPath("$.dueDate").value("2026-04-01T00:00:00Z"));
	}

	@Test
	void patchWithDueDateUpdatesDueDate() throws Exception {
		Ticket ticket = saveTicketWithStatus(TicketStatus.IN_PROGRESS);
		String request = """
			{
			  "dueDate": "2026-05-01T00:00:00Z"
			}
			""";

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.dueDate").value("2026-05-01T00:00:00Z"));
	}

	@Test
	void updatingDescriptionOnDoneTicketFails() throws Exception {
		assertDoneTicketUpdateRejected(new UpdateTicketRequest(null, "Updated description", null, null, null));
	}

	@Test
	void updatingPriorityOnDoneTicketFails() throws Exception {
		assertDoneTicketUpdateRejected(new UpdateTicketRequest(null, null, null, TicketPriority.LOW, null));
	}

	@Test
	void updatingAssigneeOnDoneTicketFails() throws Exception {
		User assignee = saveUser("newdev", "newdev@example.com", "New Developer", UserRole.DEVELOPER);
		assertDoneTicketUpdateRejected(new UpdateTicketRequest(null, null, null, null, assignee.getId()));
	}

	@Test
	void updatingDueDateOnDoneTicketFails() throws Exception {
		Ticket ticket = saveTicketWithStatus(TicketStatus.DONE);
		String request = """
			{
			  "dueDate": "2026-05-01T00:00:00Z"
			}
			""";

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("DONE")))
			.andExpect(jsonPath("$.message").value("Ticket cannot be updated because it is already DONE."));
	}

	@Test
	void updatingStatusFromDoneToDoneFails() throws Exception {
		assertDoneTicketUpdateRejected(new UpdateTicketRequest(null, null, TicketStatus.DONE, null, null));
	}

	@Test
	void ticketEntityHasVersionField() throws Exception {
		Field versionField = Ticket.class.getDeclaredField("version");

		Assertions.assertThat(versionField.getAnnotation(Version.class)).isNotNull();
	}

	@Test
	void deleteTicketPerformsSoftDelete() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		Ticket ticket = saveTicket("Fix login bug", project.getId(), null, null);

		mockMvc.perform(delete("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk());

		Ticket deletedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
		Assertions.assertThat(deletedTicket.getDeletedAt()).isNotNull();
	}

	@Test
	void deletedTicketIsNotReturnedByGetTickets() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		Ticket ticket = saveTicket("Fix login bug", project.getId(), null, null);

		mockMvc.perform(delete("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void deletedTicketIsNotReturnedByGetTicketById() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		Ticket ticket = saveTicket("Fix login bug", project.getId(), null, Instant.now());

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found: " + ticket.getId()));
	}

	@Test
	void invalidStatusReturnsInformativeBadRequest() throws Exception {
		String request = """
			{
			  "title": "Fix login bug",
			  "status": "BLOCKED",
			  "priority": "HIGH",
			  "type": "BUG",
			  "projectId": 1
			}
			""";

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("status must be one of: TODO, IN_PROGRESS, IN_REVIEW, DONE"));
	}

	@Test
	void invalidPriorityReturnsInformativeBadRequest() throws Exception {
		String request = """
			{
			  "title": "Fix login bug",
			  "status": "TODO",
			  "priority": "URGENT",
			  "type": "BUG",
			  "projectId": 1
			}
			""";

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("priority must be one of: LOW, MEDIUM, HIGH, CRITICAL"));
	}

	@Test
	void invalidTypeReturnsInformativeBadRequest() throws Exception {
		String request = """
			{
			  "title": "Fix login bug",
			  "status": "TODO",
			  "priority": "HIGH",
			  "type": "TASK",
			  "projectId": 1
			}
			""";

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("type must be one of: BUG, FEATURE, TECHNICAL"));
	}

	@Test
	void createTicketValidationErrorsAreInformative() throws Exception {
		String request = """
			{
			  "title": " ",
			  "description": "Login fails for valid users",
			  "status": null,
			  "priority": null,
			  "type": null,
			  "projectId": null
			}
			""";

		mockMvc.perform(post("/tickets")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.fieldErrors.title").value("title is required"))
			.andExpect(jsonPath("$.fieldErrors.status").value("status is required"))
			.andExpect(jsonPath("$.fieldErrors.priority").value("priority is required"))
			.andExpect(jsonPath("$.fieldErrors.type").value("type is required"))
			.andExpect(jsonPath("$.fieldErrors.projectId").value("projectId is required"));
	}

	private User saveUser(String username, String email, String fullName, UserRole role) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName(fullName);
		user.setRole(role);
		return userRepository.save(user);
	}

	private Project saveProject(String name, Long ownerId, Instant deletedAt) {
		Project project = new Project();
		project.setName(name);
		project.setDescription("A sample project");
		project.setOwnerId(ownerId);
		project.setDeletedAt(deletedAt);
		return projectRepository.save(project);
	}

	private Ticket saveTicket(String title, Long projectId, Long assigneeId, Instant deletedAt) {
		return saveTicket(title, projectId, assigneeId, deletedAt, TicketStatus.TODO);
	}

	private Ticket saveTicket(String title, Long projectId, Long assigneeId, Instant deletedAt, TicketStatus status) {
		Ticket ticket = new Ticket();
		ticket.setTitle(title);
		ticket.setDescription("Login fails for valid users");
		ticket.setStatus(status);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		ticket.setAssigneeId(assigneeId);
		ticket.setDueDate(DUE_DATE);
		ticket.setDeletedAt(deletedAt);
		return ticketRepository.save(ticket);
	}

	private Ticket saveTicketWithStatus(TicketStatus status) {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", owner.getId(), null);
		return saveTicket("Fix login bug", project.getId(), null, null, status);
	}

	private void assertStatusTransitionAllowed(TicketStatus currentStatus, TicketStatus requestedStatus) throws Exception {
		Ticket ticket = saveTicketWithStatus(currentStatus);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, requestedStatus, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value(requestedStatus.name()));
	}

	private void assertStatusTransitionRejected(
		TicketStatus currentStatus,
		TicketStatus requestedStatus,
		String expectedMessage
	) throws Exception {
		Ticket ticket = saveTicketWithStatus(currentStatus);
		UpdateTicketRequest request = new UpdateTicketRequest(null, null, requestedStatus, null, null);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString(currentStatus.name())))
			.andExpect(jsonPath("$.message", containsString(requestedStatus.name())))
			.andExpect(jsonPath("$.message").value(expectedMessage));
	}

	private void assertDoneTicketUpdateRejected(UpdateTicketRequest request) throws Exception {
		Ticket ticket = saveTicketWithStatus(TicketStatus.DONE);

		mockMvc.perform(patch("/tickets/{ticketId}", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message", not(emptyString())))
			.andExpect(jsonPath("$.message", containsString("DONE")))
			.andExpect(jsonPath("$.message").value("Ticket cannot be updated because it is already DONE."));
	}
}
