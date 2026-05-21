package com.att.tdp.issueflow.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.entity.Attachment;
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
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class AttachmentControllerIntegrationTest {

	private static final int TEN_MB_PLUS_ONE_BYTE = 10 * 1024 * 1024 + 1;

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
	void uploadPngSuccessfully() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("screenshot.png", "image/png", "png".getBytes())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.ticketId").value(ticket.getId()))
			.andExpect(jsonPath("$.filename").value("screenshot.png"))
			.andExpect(jsonPath("$.contentType").value("image/png"));

		Attachment attachment = attachmentRepository.findAll().getFirst();
		Assertions.assertThat(attachment.getData()).containsExactly("png".getBytes());
		Assertions.assertThat(attachment.getSizeBytes()).isEqualTo(3);
	}

	@Test
	void uploadJpegSuccessfully() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("photo.jpg", "image/jpeg", "jpg".getBytes())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.filename").value("photo.jpg"))
			.andExpect(jsonPath("$.contentType").value("image/jpeg"));
	}

	@Test
	void uploadPdfSuccessfully() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("report.pdf", "application/pdf", "%PDF".getBytes())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.filename").value("report.pdf"))
			.andExpect(jsonPath("$.contentType").value("application/pdf"));
	}

	@Test
	void uploadTextSuccessfully() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("notes.txt", "text/plain", "hello".getBytes())))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.filename").value("notes.txt"))
			.andExpect(jsonPath("$.contentType").value("text/plain"));
	}

	@Test
	void uploadFailsWhenTicketDoesNotExist() throws Exception {
		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", 999L)
				.file(file("notes.txt", "text/plain", "hello".getBytes())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found: 999"));
	}

	@Test
	void uploadFailsWhenFileIsEmpty() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("empty.txt", "text/plain", new byte[0])))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("file is required and must not be empty"));
	}

	@Test
	void uploadFailsWhenFilePartIsMissing() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").isNotEmpty());
	}

	@Test
	void uploadFailsForUnsupportedContentType() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("payload.json", "application/json", "{}".getBytes())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Unsupported content type: application/json"));
	}

	@Test
	void uploadFailsWhenFileExceedsTenMb() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("large.txt", "text/plain", new byte[TEN_MB_PLUS_ONE_BYTE])))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("file must be 10 MB or smaller"));
	}

	@Test
	void deleteAttachmentSuccessfully() throws Exception {
		Ticket ticket = saveTicket();
		Attachment attachment = saveAttachment(ticket.getId(), "notes.txt", "text/plain");

		mockMvc.perform(delete(
				"/tickets/{ticketId}/attachments/{attachmentId}",
				ticket.getId(),
				attachment.getId()
			))
			.andExpect(status().isOk());

		Assertions.assertThat(attachmentRepository.existsById(attachment.getId())).isFalse();
	}

	@Test
	void deleteAttachmentFailsWhenAttachmentDoesNotExist() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(delete(
				"/tickets/{ticketId}/attachments/{attachmentId}",
				ticket.getId(),
				999L
			))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Attachment not found: 999"));
	}

	@Test
	void deleteAttachmentFailsWhenAttachmentBelongsToAnotherTicket() throws Exception {
		Ticket urlTicket = saveTicket();
		Ticket otherTicket = saveTicket();
		Attachment attachment = saveAttachment(otherTicket.getId(), "notes.txt", "text/plain");

		mockMvc.perform(delete(
				"/tickets/{ticketId}/attachments/{attachmentId}",
				urlTicket.getId(),
				attachment.getId()
			))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(
				"Attachment " + attachment.getId() + " does not belong to ticket " + urlTicket.getId()
			));
	}

	@Test
	void uploadCreatesAuditLog() throws Exception {
		Ticket ticket = saveTicket();

		mockMvc.perform(multipart("/tickets/{ticketId}/attachments", ticket.getId())
				.file(file("notes.txt", "text/plain", "hello".getBytes())))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Attachment attachment = attachmentRepository.findAll().getFirst();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.UPLOAD_ATTACHMENT);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.ATTACHMENT);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(attachment.getId());
	}

	@Test
	void deleteCreatesAuditLog() throws Exception {
		Ticket ticket = saveTicket();
		Attachment attachment = saveAttachment(ticket.getId(), "notes.txt", "text/plain");

		mockMvc.perform(delete(
				"/tickets/{ticketId}/attachments/{attachmentId}",
				ticket.getId(),
				attachment.getId()
			))
			.andExpect(status().isOk());

		AuditLog auditLog = singleAuditLog();
		Assertions.assertThat(auditLog.getAction()).isEqualTo(AuditAction.DELETE_ATTACHMENT);
		Assertions.assertThat(auditLog.getEntityType()).isEqualTo(AuditEntityType.ATTACHMENT);
		Assertions.assertThat(auditLog.getEntityId()).isEqualTo(attachment.getId());
	}

	private MockMultipartFile file(String filename, String contentType, byte[] content) {
		return new MockMultipartFile("file", filename, contentType, content);
	}

	private AuditLog singleAuditLog() {
		List<AuditLog> auditLogs = auditLogRepository.findAll();
		Assertions.assertThat(auditLogs).hasSize(1);
		return auditLogs.getFirst();
	}

	private Attachment saveAttachment(Long ticketId, String filename, String contentType) {
		Attachment attachment = new Attachment();
		attachment.setTicketId(ticketId);
		attachment.setFilename(filename);
		attachment.setContentType(contentType);
		attachment.setSizeBytes(5L);
		attachment.setData("hello".getBytes());
		return attachmentRepository.save(attachment);
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
