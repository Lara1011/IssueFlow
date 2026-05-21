package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AddDependencyRequest;
import com.att.tdp.issueflow.dto.TicketDependencyResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.BusinessRuleException;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.TicketDependencyRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketDependencyService {

	private final TicketDependencyRepository ticketDependencyRepository;
	private final TicketRepository ticketRepository;
	private final AuditLogService auditLogService;

	public TicketDependencyService(
		TicketDependencyRepository ticketDependencyRepository,
		TicketRepository ticketRepository,
		AuditLogService auditLogService
	) {
		this.ticketDependencyRepository = ticketDependencyRepository;
		this.ticketRepository = ticketRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public void addDependency(Long ticketId, AddDependencyRequest request) {
		Ticket ticket = findActiveTicket(ticketId, "Ticket not found: " + ticketId);
		Ticket blocker = findActiveTicket(request.blockedBy(), "Blocker ticket not found: " + request.blockedBy());
		validateDependency(ticket, blocker);

		ticketDependencyRepository.findByTicketIdAndBlockedByTicketId(ticketId, request.blockedBy())
			.ifPresentOrElse(
				dependency -> { },
				() -> createDependency(ticketId, request.blockedBy())
			);
	}

	@Transactional(readOnly = true)
	public List<TicketDependencyResponse> getDependencies(Long ticketId) {
		findActiveTicket(ticketId, "Ticket not found: " + ticketId);
		List<TicketDependency> dependencies = ticketDependencyRepository.findAllByTicketId(ticketId);
		if (dependencies.isEmpty()) {
			return List.of();
		}

		Map<Long, Ticket> blockerTicketsById = ticketRepository.findAllById(
				dependencies.stream()
					.map(TicketDependency::getBlockedByTicketId)
					.toList()
			)
			.stream()
			.filter(ticket -> ticket.getDeletedAt() == null)
			.collect(Collectors.toMap(Ticket::getId, Function.identity()));

		return dependencies.stream()
			.map(dependency -> blockerTicketsById.get(dependency.getBlockedByTicketId()))
			.filter(ticket -> ticket != null)
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public void removeDependency(Long ticketId, Long blockerId) {
		findActiveTicket(ticketId, "Ticket not found: " + ticketId);
		findActiveTicket(blockerId, "Blocker ticket not found: " + blockerId);

		ticketDependencyRepository.findByTicketIdAndBlockedByTicketId(ticketId, blockerId)
			.ifPresent(this::deleteDependency);
	}

	private void createDependency(Long ticketId, Long blockerId) {
		TicketDependency dependency = new TicketDependency();
		dependency.setTicketId(ticketId);
		dependency.setBlockedByTicketId(blockerId);
		TicketDependency savedDependency = ticketDependencyRepository.save(dependency);
		auditLogService.record(
			AuditAction.ADD_DEPENDENCY,
			AuditEntityType.DEPENDENCY,
			savedDependency.getId(),
			null,
			AuditActor.USER
		);
	}

	private void deleteDependency(TicketDependency dependency) {
		Long dependencyId = dependency.getId();
		ticketDependencyRepository.delete(dependency);
		auditLogService.record(
			AuditAction.REMOVE_DEPENDENCY,
			AuditEntityType.DEPENDENCY,
			dependencyId,
			null,
			AuditActor.USER
		);
	}

	private Ticket findActiveTicket(Long ticketId, String message) {
		return ticketRepository.findByIdAndDeletedAtIsNull(ticketId)
			.orElseThrow(() -> new ResourceNotFoundException(message));
	}

	private void validateDependency(Ticket ticket, Ticket blocker) {
		if (ticket.getId().equals(blocker.getId())) {
			throw new BusinessRuleException("Ticket cannot depend on itself.");
		}
		if (!ticket.getProjectId().equals(blocker.getProjectId())) {
			throw new BusinessRuleException("Tickets must belong to the same project.");
		}
	}

	private TicketDependencyResponse toResponse(Ticket ticket) {
		return new TicketDependencyResponse(ticket.getId(), ticket.getTitle(), ticket.getStatus());
	}
}
