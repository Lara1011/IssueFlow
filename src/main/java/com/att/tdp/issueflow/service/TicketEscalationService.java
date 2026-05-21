package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.repository.TicketRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketEscalationService {

	private final TicketRepository ticketRepository;
	private final AuditLogService auditLogService;

	public TicketEscalationService(TicketRepository ticketRepository, AuditLogService auditLogService) {
		this.ticketRepository = ticketRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public int runEscalationCycle() {
		List<Ticket> overdueTickets = ticketRepository.findAllByDueDateBeforeAndDeletedAtIsNullAndStatusNot(
			Instant.now(),
			TicketStatus.DONE
		);

		int changedCount = 0;
		for (Ticket ticket : overdueTickets) {
			if (escalate(ticket)) {
				changedCount++;
				auditLogService.record(
					AuditAction.AUTO_ESCALATE,
					AuditEntityType.TICKET,
					ticket.getId(),
					null,
					AuditActor.SYSTEM
				);
			}
		}
		return changedCount;
	}

	private boolean escalate(Ticket ticket) {
		return switch (ticket.getPriority()) {
			case LOW -> {
				ticket.setPriority(TicketPriority.MEDIUM);
				ticket.setOverdue(false);
				yield true;
			}
			case MEDIUM -> {
				ticket.setPriority(TicketPriority.HIGH);
				ticket.setOverdue(false);
				yield true;
			}
			case HIGH -> {
				ticket.setPriority(TicketPriority.CRITICAL);
				ticket.setOverdue(true);
				yield true;
			}
			case CRITICAL -> {
				if (ticket.isOverdue()) {
					yield false;
				}
				ticket.setOverdue(true);
				yield true;
			}
		};
	}
}
