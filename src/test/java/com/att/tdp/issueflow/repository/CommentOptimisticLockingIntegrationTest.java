package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.enums.UserRole;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@SpringBootTest
class CommentOptimisticLockingIntegrationTest {

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
		commentRepository.deleteAll();
		ticketRepository.deleteAll();
		projectRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void staleCommentUpdateFailsWithOptimisticLocking() {
		User author = saveUser();
		Ticket ticket = saveTicket(author.getId());
		Comment savedComment = saveComment(ticket.getId(), author.getId());

		Comment staleComment = commentRepository.findById(savedComment.getId()).orElseThrow();
		Comment currentComment = commentRepository.findById(savedComment.getId()).orElseThrow();
		currentComment.setContent("Current update");
		commentRepository.saveAndFlush(currentComment);

		staleComment.setContent("Stale update");

		Assertions.assertThatThrownBy(() -> commentRepository.saveAndFlush(staleComment))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private User saveUser() {
		User user = new User();
		user.setUsername("author");
		user.setEmail("author@example.com");
		user.setFullName("Comment Author");
		user.setRole(UserRole.DEVELOPER);
		return userRepository.save(user);
	}

	private Ticket saveTicket(Long ownerId) {
		Project project = new Project();
		project.setName("Sample Project");
		project.setDescription("A sample project");
		project.setOwnerId(ownerId);
		Project savedProject = projectRepository.save(project);

		Ticket ticket = new Ticket();
		ticket.setTitle("Fix login bug");
		ticket.setDescription("Login fails for valid users");
		ticket.setStatus(TicketStatus.TODO);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(savedProject.getId());
		return ticketRepository.saveAndFlush(ticket);
	}

	private Comment saveComment(Long ticketId, Long authorId) {
		Comment comment = new Comment();
		comment.setTicketId(ticketId);
		comment.setAuthorId(authorId);
		comment.setContent("Original comment");
		return commentRepository.saveAndFlush(comment);
	}
}
