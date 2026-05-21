package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.exception.BusinessRuleException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TicketService {

	private final TicketRepository ticketRepository;
	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;

	public TicketService(
		TicketRepository ticketRepository,
		ProjectRepository projectRepository,
		UserRepository userRepository
	) {
		this.ticketRepository = ticketRepository;
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<TicketResponse> getTicketsByProject(Long projectId) {
		validateActiveProject(projectId);
		return ticketRepository.findAllByProjectIdAndDeletedAtIsNull(projectId)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public TicketResponse getTicketById(Long ticketId) {
		return toResponse(findActiveTicket(ticketId));
	}

	@Transactional
	public TicketResponse createTicket(CreateTicketRequest request) {
		validateActiveProject(request.projectId());
		validateAssigneeIfPresent(request.assigneeId());

		Ticket ticket = new Ticket();
		ticket.setTitle(request.title());
		ticket.setDescription(request.description());
		ticket.setStatus(request.status());
		ticket.setPriority(request.priority());
		ticket.setType(request.type());
		ticket.setProjectId(request.projectId());
		ticket.setAssigneeId(request.assigneeId());
		ticket.setDueDate(request.dueDate());

		return toResponse(ticketRepository.save(ticket));
	}

	@Transactional
	public void updateTicket(Long ticketId, UpdateTicketRequest request) {
		validateUpdateRequest(request);

		Ticket ticket = findActiveTicket(ticketId);
		validateTicketCanBeUpdated(ticket);
		validateStatusTransition(ticket.getStatus(), request.status());
		validateAssigneeIfPresent(request.assigneeId());

		if (request.title() != null) {
			ticket.setTitle(request.title());
		}
		if (request.description() != null) {
			ticket.setDescription(request.description());
		}
		if (request.status() != null) {
			ticket.setStatus(request.status());
		}
		if (request.priority() != null) {
			ticket.setPriority(request.priority());
		}
		if (request.assigneeId() != null) {
			ticket.setAssigneeId(request.assigneeId());
		}
	}

	@Transactional
	public void deleteTicket(Long ticketId) {
		Ticket ticket = findActiveTicket(ticketId);
		ticket.setDeletedAt(Instant.now());
	}

	private Ticket findActiveTicket(Long ticketId) {
		return ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
	}

	private void validateActiveProject(Long projectId) {
		if (projectId == null || projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
	}

	private void validateAssigneeIfPresent(Long assigneeId) {
		if (assigneeId != null && !userRepository.existsById(assigneeId)) {
			throw new ResourceNotFoundException("Assignee not found: " + assigneeId);
		}
	}

	private void validateUpdateRequest(UpdateTicketRequest request) {
		if (request.title() != null && !StringUtils.hasText(request.title())) {
			throw new IllegalArgumentException("title must not be blank");
		}
	}

	private void validateTicketCanBeUpdated(Ticket ticket) {
		if (ticket.getStatus() == TicketStatus.DONE) {
			throw new BusinessRuleException("Ticket cannot be updated because it is already DONE");
		}
	}

	private void validateStatusTransition(TicketStatus currentStatus, TicketStatus requestedStatus) {
		if (requestedStatus == null || requestedStatus == currentStatus) {
			return;
		}
		if (lifecyclePosition(requestedStatus) < lifecyclePosition(currentStatus)) {
			throw new BusinessRuleException(
				"Invalid status transition: " + currentStatus + " cannot move back to " + requestedStatus
			);
		}
	}

	private int lifecyclePosition(TicketStatus status) {
		return switch (status) {
			case TODO -> 0;
			case IN_PROGRESS -> 1;
			case IN_REVIEW -> 2;
			case DONE -> 3;
		};
	}

	private TicketResponse toResponse(Ticket ticket) {
		return new TicketResponse(
			ticket.getId(),
			ticket.getTitle(),
			ticket.getDescription(),
			ticket.getStatus(),
			ticket.getPriority(),
			ticket.getType(),
			ticket.getProjectId(),
			ticket.getAssigneeId(),
			ticket.getDueDate(),
			false
		);
	}
}
