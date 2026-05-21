package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateTicketRequest(
	String title,
	String description,
	TicketStatus status,
	TicketPriority priority,
	Long assigneeId
) {
}
