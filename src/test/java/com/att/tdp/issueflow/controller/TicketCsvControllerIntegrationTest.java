package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class TicketCsvControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

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
	void exportTicketsSuccessfully() throws Exception {
		Project project = saveProject();
		User assignee = saveUser("dev", "dev@example.com");
		Ticket ticket = saveTicket(project.getId(), "Export me", "Description", assignee.getId(), null);

		MvcResult result = mockMvc.perform(get("/tickets/export").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("text/csv")))
			.andExpect(header().string(
				HttpHeaders.CONTENT_DISPOSITION,
				"attachment; filename=\"tickets-project-" + project.getId() + ".csv\""
			))
			.andReturn();

		List<CSVRecord> records = parseCsv(result.getResponse().getContentAsString());
		Assertions.assertThat(records).hasSize(1);
		Assertions.assertThat(records.getFirst().get("id")).isEqualTo(ticket.getId().toString());
		Assertions.assertThat(records.getFirst().get("title")).isEqualTo("Export me");
		Assertions.assertThat(records.getFirst().get("assigneeId")).isEqualTo(assignee.getId().toString());
	}

	@Test
	void exportIncludesCsvHeaderExactly() throws Exception {
		Project project = saveProject();

		MvcResult result = mockMvc.perform(get("/tickets/export").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andReturn();

		String firstLine = result.getResponse().getContentAsString().split("\\R", -1)[0];
		Assertions.assertThat(firstLine).isEqualTo("id,title,description,status,priority,type,assigneeId");
	}

	@Test
	void exportIncludesOnlyNonDeletedTicketsForProject() throws Exception {
		Project project = saveProject();
		Project otherProject = saveProject();
		saveTicket(project.getId(), "Visible", null, null, null);
		saveTicket(project.getId(), "Deleted", null, null, Instant.now());
		saveTicket(otherProject.getId(), "Other project", null, null, null);

		MvcResult result = mockMvc.perform(get("/tickets/export").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andReturn();

		List<CSVRecord> records = parseCsv(result.getResponse().getContentAsString());
		Assertions.assertThat(records).extracting(record -> record.get("title")).containsExactly("Visible");
	}

	@Test
	void exportHandlesCommasAndQuotesInTitleAndDescription() throws Exception {
		Project project = saveProject();
		saveTicket(project.getId(), "Fix \"login\", now", "Line one, \"quoted\"\nLine two", null, null);

		MvcResult result = mockMvc.perform(get("/tickets/export").param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andReturn();

		List<CSVRecord> records = parseCsv(result.getResponse().getContentAsString());
		Assertions.assertThat(records.getFirst().get("title")).isEqualTo("Fix \"login\", now");
		Assertions.assertThat(records.getFirst().get("description")).isEqualTo("Line one, \"quoted\"\nLine two");
	}

	@Test
	void exportFailsWhenProjectDoesNotExist() throws Exception {
		mockMvc.perform(get("/tickets/export").param("projectId", "999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found: 999"));
	}

	@Test
	void exportCreatesAuditLog() throws Exception {
		Project project = saveProject();

		mockMvc.perform(get("/tickets/export").param("projectId", project.getId().toString()))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.EXPORT);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.TICKET);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(project.getId());
	}

	@Test
	void importTicketsSuccessfully() throws Exception {
		Project project = saveProject();
		User assignee = saveUser("assignee", "assignee@example.com");
		String csv = """
			title,description,status,priority,type,assigneeId
			Imported one,First ticket,TODO,HIGH,BUG,%d
			Imported two,Second ticket,IN_PROGRESS,MEDIUM,FEATURE,
			""".formatted(assignee.getId());

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(2))
			.andExpect(jsonPath("$.failed").value(0))
			.andExpect(jsonPath("$.errors", hasSize(0)));

		Assertions.assertThat(ticketRepository.findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(project.getId()))
			.extracting(Ticket::getTitle)
			.containsExactly("Imported one", "Imported two");
	}

	@Test
	void importIgnoresIdColumnIfPresent() throws Exception {
		Project project = saveProject();
		String csv = """
			id,title,description,status,priority,type,assigneeId
			999,Imported with new id,Description,TODO,HIGH,BUG,
			""";

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(1));

		Ticket ticket = ticketRepository.findAll().getFirst();
		Assertions.assertThat(ticket.getId()).isNotEqualTo(999L);
		Assertions.assertThat(ticket.getTitle()).isEqualTo("Imported with new id");
	}

	@Test
	void importCreatesTicketsUnderMultipartProjectId() throws Exception {
		Project project = saveProject();
		Project otherProject = saveProject();
		String csv = """
			title,description,status,priority,type,assigneeId
			Imported,Description,TODO,HIGH,BUG,
			""";

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk());

		Ticket ticket = ticketRepository.findAll().getFirst();
		Assertions.assertThat(ticket.getProjectId()).isEqualTo(project.getId());
		Assertions.assertThat(ticket.getProjectId()).isNotEqualTo(otherProject.getId());
	}

	@Test
	void importHandlesCommasAndQuotesInFieldValues() throws Exception {
		Project project = saveProject();
		String csv = """
			title,description,status,priority,type,assigneeId
			"Fix ""login"", now","Line one, ""quoted""
			Line two",TODO,HIGH,BUG,
			""";

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(1));

		Ticket ticket = ticketRepository.findAll().getFirst();
		Assertions.assertThat(ticket.getTitle()).isEqualTo("Fix \"login\", now");
		Assertions.assertThat(ticket.getDescription()).isEqualTo("Line one, \"quoted\"\nLine two");
	}

	@Test
	void importContinuesAfterRowFailuresAndReturnsSummary() throws Exception {
		Project project = saveProject();
		String csv = """
			title,description,status,priority,type,assigneeId
			Valid row,Description,TODO,HIGH,BUG,
			,Missing title,TODO,HIGH,BUG,
			Invalid status,Description,BLOCKED,HIGH,BUG,
			""";

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(csv))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(1))
			.andExpect(jsonPath("$.failed").value(2))
			.andExpect(jsonPath("$.errors", hasSize(2)))
			.andExpect(jsonPath("$.errors[0].row").value(3))
			.andExpect(jsonPath("$.errors[0].message").value("title must not be blank"))
			.andExpect(jsonPath("$.errors[1].row").value(4))
			.andExpect(jsonPath("$.errors[1].message").value(
				"status must be one of: TODO, IN_PROGRESS, IN_REVIEW, DONE"
			));

		Assertions.assertThat(ticketRepository.findAll()).hasSize(1);
	}

	@Test
	void importFailsRowWithInvalidStatus() throws Exception {
		Project project = saveProject();

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(rowCsv("Invalid status", "BLOCKED", "HIGH", "BUG", "")))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.errors[0].message").value(
				"status must be one of: TODO, IN_PROGRESS, IN_REVIEW, DONE"
			));
	}

	@Test
	void importFailsRowWithInvalidPriority() throws Exception {
		Project project = saveProject();

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(rowCsv("Invalid priority", "TODO", "URGENT", "BUG", "")))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.errors[0].message").value(
				"priority must be one of: LOW, MEDIUM, HIGH, CRITICAL"
			));
	}

	@Test
	void importFailsRowWithInvalidType() throws Exception {
		Project project = saveProject();

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(rowCsv("Invalid type", "TODO", "HIGH", "TASK", "")))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.errors[0].message").value(
				"type must be one of: BUG, FEATURE, TECHNICAL"
			));
	}

	@Test
	void importFailsRowWithMissingTitle() throws Exception {
		Project project = saveProject();

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(rowCsv("", "TODO", "HIGH", "BUG", "")))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.errors[0].message").value("title must not be blank"));
	}

	@Test
	void importFailsRowWithInvalidAssigneeId() throws Exception {
		Project project = saveProject();

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(rowCsv("Invalid assignee", "TODO", "HIGH", "BUG", "999")))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.created").value(0))
			.andExpect(jsonPath("$.failed").value(1))
			.andExpect(jsonPath("$.errors[0].message").value("Assignee not found: 999"));
	}

	@Test
	void importFailsWhenProjectDoesNotExist() throws Exception {
		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(rowCsv("Valid", "TODO", "HIGH", "BUG", "")))
				.param("projectId", "999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found: 999"));
	}

	@Test
	void importFailsWhenFileIsEmpty() throws Exception {
		Project project = saveProject();

		mockMvc.perform(multipart("/tickets/import")
				.file(new MockMultipartFile("file", "tickets.csv", "text/csv", new byte[0]))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("file is required and must not be empty"));
	}

	@Test
	void importCreatesAuditLog() throws Exception {
		Project project = saveProject();

		mockMvc.perform(multipart("/tickets/import")
				.file(csvFile(rowCsv("Valid", "TODO", "HIGH", "BUG", "")))
				.param("projectId", project.getId().toString()))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.IMPORT);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.TICKET);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(project.getId());
	}

	private List<CSVRecord> parseCsv(String csv) throws Exception {
		try (CSVParser parser = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.build()
			.parse(new StringReader(csv))) {
			return parser.getRecords();
		}
	}

	private MockMultipartFile csvFile(String csv) {
		return new MockMultipartFile("file", "tickets.csv", "text/csv", csv.getBytes());
	}

	private String rowCsv(String title, String status, String priority, String type, String assigneeId) {
		return """
			title,description,status,priority,type,assigneeId
			%s,Description,%s,%s,%s,%s
			""".formatted(title, status, priority, type, assigneeId);
	}

	private AuditLog singleAuditLog() {
		List<AuditLog> auditLogs = auditLogRepository.findAll();
		Assertions.assertThat(auditLogs).hasSize(1);
		return auditLogs.getFirst();
	}

	private User saveUser(String username, String email) {
		User user = new User();
		user.setUsername(username + System.nanoTime());
		user.setEmail(System.nanoTime() + email);
		user.setFullName("Test User");
		user.setRole(UserRole.DEVELOPER);
		return userRepository.save(user);
	}

	private Project saveProject() {
		User owner = saveUser("owner", "owner@example.com");
		Project project = new Project();
		project.setName("Sample Project");
		project.setDescription("A sample project");
		project.setOwnerId(owner.getId());
		return projectRepository.save(project);
	}

	private Ticket saveTicket(
		Long projectId,
		String title,
		String description,
		Long assigneeId,
		Instant deletedAt
	) {
		Ticket ticket = new Ticket();
		ticket.setTitle(title);
		ticket.setDescription(description);
		ticket.setStatus(TicketStatus.TODO);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		ticket.setAssigneeId(assigneeId);
		ticket.setDeletedAt(deletedAt);
		return ticketRepository.save(ticket);
	}
}
