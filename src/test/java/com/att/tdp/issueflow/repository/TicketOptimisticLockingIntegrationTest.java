package com.att.tdp.issueflow.repository;

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
class TicketOptimisticLockingIntegrationTest {

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
	void staleTicketUpdateFailsWithOptimisticLocking() {
		User owner = saveUser();
		Project project = saveProject(owner.getId());
		Ticket savedTicket = saveTicket(project.getId());

		Ticket staleTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
		Ticket currentTicket = ticketRepository.findById(savedTicket.getId()).orElseThrow();
		currentTicket.setTitle("Current update");
		ticketRepository.saveAndFlush(currentTicket);

		staleTicket.setTitle("Stale update");

		Assertions.assertThatThrownBy(() -> ticketRepository.saveAndFlush(staleTicket))
			.isInstanceOf(ObjectOptimisticLockingFailureException.class);
	}

	private User saveUser() {
		User user = new User();
		user.setUsername("owner");
		user.setEmail("owner@example.com");
		user.setFullName("Owner User");
		user.setRole(UserRole.ADMIN);
		return userRepository.save(user);
	}

	private Project saveProject(Long ownerId) {
		Project project = new Project();
		project.setName("Sample Project");
		project.setDescription("A sample project");
		project.setOwnerId(ownerId);
		return projectRepository.save(project);
	}

	private Ticket saveTicket(Long projectId) {
		Ticket ticket = new Ticket();
		ticket.setTitle("Fix login bug");
		ticket.setDescription("Login fails for valid users");
		ticket.setStatus(TicketStatus.TODO);
		ticket.setPriority(TicketPriority.HIGH);
		ticket.setType(TicketType.BUG);
		ticket.setProjectId(projectId);
		return ticketRepository.saveAndFlush(ticket);
	}
}
