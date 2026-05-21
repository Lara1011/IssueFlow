package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.CreateCommentRequest;
import com.att.tdp.issueflow.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Version;
import java.lang.reflect.Field;
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
class CommentControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CommentRepository commentRepository;

	@Autowired
	private CommentMentionRepository commentMentionRepository;

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
	}

	@Test
	void addCommentSuccessfully() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), "Hello @jdoe!");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.ticketId").value(ticket.getId()))
			.andExpect(jsonPath("$.authorId").value(author.getId()))
			.andExpect(jsonPath("$.content").value("Hello @jdoe!"))
			.andExpect(jsonPath("$.mentionedUsers", hasSize(0)));
	}

	@Test
	void addCommentFailsWhenTicketDoesNotExist() throws Exception {
		User author = saveUser("author", "author@example.com");
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), "Hello");

		mockMvc.perform(post("/tickets/{ticketId}/comments", 999L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found: 999"));
	}

	@Test
	void addCommentFailsWhenAuthorDoesNotExist() throws Exception {
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(999L, "Hello");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Author not found: 999"));
	}

	@Test
	void addCommentFailsWhenContentIsBlank() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), " ");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.fieldErrors.content").value("content is required"));
	}

	@Test
	void getCommentsForTicket() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		saveComment(ticket.getId(), author.getId(), "First comment");
		saveComment(ticket.getId(), author.getId(), "Second comment");

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[0].ticketId").value(ticket.getId()))
			.andExpect(jsonPath("$[0].mentionedUsers", hasSize(0)));
	}

	@Test
	void getCommentsFailsWhenTicketDoesNotExist() throws Exception {
		mockMvc.perform(get("/tickets/{ticketId}/comments", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Ticket not found: 999"));
	}

	@Test
	void updateCommentContentSuccessfully() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		Comment comment = saveComment(ticket.getId(), author.getId(), "Original comment");
		UpdateCommentRequest request = new UpdateCommentRequest("Updated comment.");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticket.getId(), comment.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		Comment updatedComment = commentRepository.findById(comment.getId()).orElseThrow();
		Assertions.assertThat(updatedComment.getContent()).isEqualTo("Updated comment.");
	}

	@Test
	void updateCommentFailsWhenCommentDoesNotExist() throws Exception {
		Ticket ticket = saveTicket();
		UpdateCommentRequest request = new UpdateCommentRequest("Updated comment.");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticket.getId(), 999L)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Comment not found: 999"));
	}

	@Test
	void updateCommentFailsWhenCommentBelongsToAnotherTicket() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket urlTicket = saveTicket();
		Ticket otherTicket = saveTicket();
		Comment comment = saveComment(otherTicket.getId(), author.getId(), "Other ticket comment");
		UpdateCommentRequest request = new UpdateCommentRequest("Updated comment.");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", urlTicket.getId(), comment.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(
				"Comment " + comment.getId() + " does not belong to ticket " + urlTicket.getId()
			));
	}

	@Test
	void updateCommentFailsWhenContentIsBlank() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		Comment comment = saveComment(ticket.getId(), author.getId(), "Original comment");
		UpdateCommentRequest request = new UpdateCommentRequest(" ");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticket.getId(), comment.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.fieldErrors.content").value("content is required"));
	}

	@Test
	void deleteCommentSuccessfully() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		Comment comment = saveComment(ticket.getId(), author.getId(), "Comment to delete");

		mockMvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", ticket.getId(), comment.getId()))
			.andExpect(status().isOk());

		Assertions.assertThat(commentRepository.existsById(comment.getId())).isFalse();
	}

	@Test
	void deleteCommentFailsWhenCommentBelongsToAnotherTicket() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket urlTicket = saveTicket();
		Ticket otherTicket = saveTicket();
		Comment comment = saveComment(otherTicket.getId(), author.getId(), "Other ticket comment");

		mockMvc.perform(delete("/tickets/{ticketId}/comments/{commentId}", urlTicket.getId(), comment.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(
				"Comment " + comment.getId() + " does not belong to ticket " + urlTicket.getId()
			));
	}

	@Test
	void commentResponseIncludesEmptyMentionedUsersArray() throws Exception {
		User author = saveUser("author", "author@example.com");
		Ticket ticket = saveTicket();
		saveComment(ticket.getId(), author.getId(), "Hello @jdoe!");

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].mentionedUsers", hasSize(0)));
	}

	@Test
	void commentEntityHasVersionField() throws Exception {
		Field versionField = Comment.class.getDeclaredField("version");

		Assertions.assertThat(versionField.getAnnotation(Version.class)).isNotNull();
	}

	private User saveUser(String username, String email) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName("Test User");
		user.setRole(UserRole.DEVELOPER);
		return userRepository.save(user);
	}

	private Ticket saveTicket() {
		User owner = saveUser("owner" + System.nanoTime(), "owner" + System.nanoTime() + "@example.com");
		Project project = new Project();
		project.setName("Sample Project");
		project.setDescription("A sample project");
		project.setOwnerId(owner.getId());
		Project savedProject = projectRepository.save(project);

		Ticket ticket = new Ticket();
		ticket.setTitle("Fix login bug");
		ticket.setDescription("Login fails for valid users");
		ticket.setStatus(TicketStatus.TODO);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(savedProject.getId());
		return ticketRepository.save(ticket);
	}

	private Comment saveComment(Long ticketId, Long authorId, String content) {
		Comment comment = new Comment();
		comment.setTicketId(ticketId);
		comment.setAuthorId(authorId);
		comment.setContent(content);
		return commentRepository.save(comment);
	}
}
