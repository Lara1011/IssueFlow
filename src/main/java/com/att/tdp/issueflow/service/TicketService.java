package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.exception.BusinessRuleException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
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
	private final TicketDependencyRepository ticketDependencyRepository;
	private final UserRepository userRepository;
	private final AuditLogService auditLogService;
	private final WorkloadService workloadService;

	public TicketService(
		TicketRepository ticketRepository,
		ProjectRepository projectRepository,
		TicketDependencyRepository ticketDependencyRepository,
		UserRepository userRepository,
		AuditLogService auditLogService,
		WorkloadService workloadService
	) {
		this.ticketRepository = ticketRepository;
		this.projectRepository = projectRepository;
		this.ticketDependencyRepository = ticketDependencyRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
		this.workloadService = workloadService;
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

	@Transactional(readOnly = true)
	public List<TicketResponse> getDeletedTicketsByProject(Long projectId) {
		validateProjectExists(projectId);
		return ticketRepository.findAllByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDescIdDesc(projectId)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public TicketResponse createTicket(CreateTicketRequest request) {
		validateActiveProject(request.projectId());
		validateAssigneeIfPresent(request.assigneeId());
		Long assigneeId = request.assigneeId();
		boolean autoAssigned = false;
		if (assigneeId == null) {
			assigneeId = workloadService.findLeastLoadedDeveloper(request.projectId())
				.map(user -> user.getId())
				.orElse(null);
			autoAssigned = assigneeId != null;
		}

		Ticket ticket = new Ticket();
		ticket.setTitle(request.title());
		ticket.setDescription(request.description());
		ticket.setStatus(request.status());
		ticket.setPriority(request.priority());
		ticket.setType(request.type());
		ticket.setProjectId(request.projectId());
		ticket.setAssigneeId(assigneeId);
		ticket.setDueDate(request.dueDate());

		Ticket savedTicket = ticketRepository.save(ticket);
		auditLogService.record(AuditAction.CREATE, AuditEntityType.TICKET, savedTicket.getId(), null, AuditActor.USER);
		if (autoAssigned) {
			auditLogService.record(
				AuditAction.AUTO_ASSIGN,
				AuditEntityType.TICKET,
				savedTicket.getId(),
				null,
				AuditActor.SYSTEM
			);
		}
		return toResponse(savedTicket);
	}

	@Transactional
	public void updateTicket(Long ticketId, UpdateTicketRequest request) {
		validateUpdateRequest(request);

		Ticket ticket = findActiveTicket(ticketId);
		validateTicketCanBeUpdated(ticket);
		validateStatusTransition(ticket.getStatus(), request.status());
		validateBlockersResolvedForDoneTransition(ticket, request.status());
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
			if (request.priority() != ticket.getPriority()) {
				ticket.setOverdue(false);
			}
			ticket.setPriority(request.priority());
		}
		if (request.assigneeId() != null) {
			ticket.setAssigneeId(request.assigneeId());
		}
		if (request.dueDate() != null) {
			ticket.setDueDate(request.dueDate());
		}
		auditLogService.record(AuditAction.UPDATE, AuditEntityType.TICKET, ticket.getId(), null, AuditActor.USER);
	}

	@Transactional
	public void deleteTicket(Long ticketId) {
		Ticket ticket = findActiveTicket(ticketId);
		ticket.setDeletedAt(Instant.now());
		auditLogService.record(AuditAction.DELETE, AuditEntityType.TICKET, ticket.getId(), null, AuditActor.USER);
	}

	@Transactional
	public void restoreTicket(Long ticketId) {
		Ticket ticket = findTicket(ticketId);
		if (ticket.getDeletedAt() == null) {
			return;
		}
		if (projectIsDeleted(ticket.getProjectId())) {
			throw new BusinessRuleException("Cannot restore ticket because its project is deleted.");
		}
		ticket.setDeletedAt(null);
		auditLogService.record(AuditAction.RESTORE, AuditEntityType.TICKET, ticket.getId(), null, AuditActor.USER);
	}

	private Ticket findTicket(Long ticketId) {
		return ticketRepository.findById(ticketId)
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
	}

	private Ticket findActiveTicket(Long ticketId) {
		return ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
			.orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
	}

	private void validateProjectExists(Long projectId) {
		if (projectId == null || !projectRepository.existsById(projectId)) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
	}

	private boolean projectIsDeleted(Long projectId) {
		return projectRepository.findById(projectId)
			.map(project -> project.getDeletedAt() != null)
			.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
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
			throw new BusinessRuleException("Ticket cannot be updated because it is already DONE.");
		}
	}

	private void validateStatusTransition(TicketStatus currentStatus, TicketStatus requestedStatus) {
		if (requestedStatus == null || requestedStatus == currentStatus) {
			return;
		}
		TicketStatus allowedNextStatus = allowedNextStatus(currentStatus);
		if (requestedStatus != allowedNextStatus) {
			throw new BusinessRuleException(
				"Invalid ticket status transition from " + currentStatus + " to " + requestedStatus
					+ ". Allowed next status is " + allowedNextStatus + "."
			);
		}
	}

	private void validateBlockersResolvedForDoneTransition(Ticket ticket, TicketStatus requestedStatus) {
		if (requestedStatus == TicketStatus.DONE
			&& ticketDependencyRepository.hasUnresolvedBlockers(ticket.getId(), TicketStatus.DONE)) {
			throw new BusinessRuleException("Ticket cannot be marked DONE because it has unresolved blockers.");
		}
	}

	private TicketStatus allowedNextStatus(TicketStatus status) {
		return switch (status) {
			case TODO -> TicketStatus.IN_PROGRESS;
			case IN_PROGRESS -> TicketStatus.IN_REVIEW;
			case IN_REVIEW -> TicketStatus.DONE;
			case DONE -> throw new BusinessRuleException("Ticket cannot be updated because it is already DONE.");
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
			ticket.isOverdue()
		);
	}
}
