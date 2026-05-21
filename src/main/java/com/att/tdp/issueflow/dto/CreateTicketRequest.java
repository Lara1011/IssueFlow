package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateTicketRequest(
	@NotBlank(message = "title is required") String title,
	String description,
	@NotNull(message = "status is required") TicketStatus status,
	@NotNull(message = "priority is required") TicketPriority priority,
	@NotNull(message = "type is required") TicketType type,
	@NotNull(message = "projectId is required") Long projectId,
	Long assigneeId,
	Instant dueDate
) {
}
