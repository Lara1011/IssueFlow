package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import java.time.Instant;

public record UpdateTicketRequest(
	String title,
	String description,
	TicketStatus status,
	TicketPriority priority,
	Long assigneeId,
	Instant dueDate
) {
}
