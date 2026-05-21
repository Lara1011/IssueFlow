package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
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
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class MentionControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

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
	}

	@Test
	void addCommentWithExistingUsernameCreatesMentionAndReturnsMentionedUser() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), "Hello @jdoe");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedUsers", hasSize(1)))
			.andExpect(jsonPath("$.mentionedUsers[0].id").value(mentionedUser.getId()))
			.andExpect(jsonPath("$.mentionedUsers[0].username").value("jdoe"))
			.andExpect(jsonPath("$.mentionedUsers[0].fullName").value("John Doe"));
	}

	@Test
	void mentionMatchingIsCaseInsensitive() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), "Hello @JDOE");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedUsers", hasSize(1)))
			.andExpect(jsonPath("$.mentionedUsers[0].id").value(mentionedUser.getId()));
	}

	@Test
	void unknownUsernameIsIgnored() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), "Hello @missing");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedUsers", hasSize(0)));

		Assertions.assertThat(commentMentionRepository.findAll()).isEmpty();
	}

	@Test
	void duplicateUsernameInSameCommentCreatesOneMentionAssociation() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), "@jdoe please check this @JDOE");

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.mentionedUsers", hasSize(1)))
			.andExpect(jsonPath("$.mentionedUsers[0].id").value(mentionedUser.getId()));

		Comment comment = commentRepository.findAll().getFirst();
		Assertions.assertThat(commentMentionRepository.findAllByCommentId(comment.getId())).hasSize(1);
	}

	@Test
	void getCommentsForTicketIncludesMentionedUsers() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		addComment(ticket, author, "Hello @jdoe");

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].mentionedUsers", hasSize(1)))
			.andExpect(jsonPath("$[0].mentionedUsers[0].id").value(mentionedUser.getId()))
			.andExpect(jsonPath("$[0].mentionedUsers[0].username").value("jdoe"));
	}

	@Test
	void updateCommentAddsNewMentions() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		Comment comment = addComment(ticket, author, "No mention yet");
		UpdateCommentRequest request = new UpdateCommentRequest("Now mention @jdoe");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticket.getId(), comment.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].mentionedUsers", hasSize(1)))
			.andExpect(jsonPath("$[0].mentionedUsers[0].id").value(mentionedUser.getId()));
	}

	@Test
	void updateCommentRemovesMentionsNoLongerPresent() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		Comment comment = addComment(ticket, author, "Hello @jdoe");
		UpdateCommentRequest request = new UpdateCommentRequest("No mentions now");

		mockMvc.perform(patch("/tickets/{ticketId}/comments/{commentId}", ticket.getId(), comment.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/tickets/{ticketId}/comments", ticket.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].mentionedUsers", hasSize(0)));
		Assertions.assertThat(commentMentionRepository.findAllByCommentId(comment.getId())).isEmpty();
	}

	@Test
	void getMentionsForUserReturnsCommentsWhereUserWasMentioned() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		addComment(ticket, author, "Hello @jdoe");
		addComment(ticket, author, "No mention");

		mockMvc.perform(get("/users/{userId}/mentions", mentionedUser.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].content").value("Hello @jdoe"))
			.andExpect(jsonPath("$.data[0].mentionedUsers", hasSize(1)))
			.andExpect(jsonPath("$.total").value(1))
			.andExpect(jsonPath("$.page").value(1));
	}

	@Test
	void getMentionsForUserReturnsNewestFirst() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		addComment(ticket, author, "Older @jdoe");
		addComment(ticket, author, "Newer @jdoe");

		mockMvc.perform(get("/users/{userId}/mentions", mentionedUser.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(2)))
			.andExpect(jsonPath("$.data[0].content").value("Newer @jdoe"))
			.andExpect(jsonPath("$.data[1].content").value("Older @jdoe"));
	}

	@Test
	void getMentionsForUserSupportsPageAndPageSize() throws Exception {
		User author = saveUser("author", "author@example.com", "Comment Author");
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");
		Ticket ticket = saveTicket();
		addComment(ticket, author, "Older @jdoe");
		addComment(ticket, author, "Newer @jdoe");

		mockMvc.perform(get("/users/{userId}/mentions", mentionedUser.getId())
				.param("page", "1")
				.param("pageSize", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data", hasSize(1)))
			.andExpect(jsonPath("$.data[0].content").value("Newer @jdoe"))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.page").value(1));
	}

	@Test
	void getMentionsForUserFailsWhenUserDoesNotExist() throws Exception {
		mockMvc.perform(get("/users/{userId}/mentions", 999L))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("User not found: 999"));
	}

	@Test
	void invalidPageReturnsInformativeBadRequest() throws Exception {
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");

		mockMvc.perform(get("/users/{userId}/mentions", mentionedUser.getId())
				.param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("page must be greater than or equal to 1")));
	}

	@Test
	void invalidPageSizeReturnsInformativeBadRequest() throws Exception {
		User mentionedUser = saveUser("jdoe", "jdoe@example.com", "John Doe");

		mockMvc.perform(get("/users/{userId}/mentions", mentionedUser.getId())
				.param("pageSize", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(containsString("pageSize must be greater than or equal to 1")));
	}

	private Comment addComment(Ticket ticket, User author, String content) throws Exception {
		CreateCommentRequest request = new CreateCommentRequest(author.getId(), content);

		mockMvc.perform(post("/tickets/{ticketId}/comments", ticket.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		return commentRepository.findAll()
			.stream()
			.filter(comment -> comment.getContent().equals(content))
			.findFirst()
			.orElseThrow();
	}

	private User saveUser(String username, String email, String fullName) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName(fullName);
		user.setRole(UserRole.DEVELOPER);
		return userRepository.save(user);
	}

	private Ticket saveTicket() {
		User owner = saveUser("owner" + System.nanoTime(), "owner" + System.nanoTime() + "@example.com", "Project Owner");
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
}
