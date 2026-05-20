package com.att.tdp.issueflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "ticket_dependencies",
	uniqueConstraints = @UniqueConstraint(columnNames = {"ticketId", "blockedByTicketId"})
)
public class TicketDependency extends BaseEntity {

	@Column(nullable = false)
	private Long ticketId;

	@Column(nullable = false)
	private Long blockedByTicketId;

	public Long getTicketId() {
		return ticketId;
	}

	public void setTicketId(Long ticketId) {
		this.ticketId = ticketId;
	}

	public Long getBlockedByTicketId() {
		return blockedByTicketId;
	}

	public void setBlockedByTicketId(Long blockedByTicketId) {
		this.blockedByTicketId = blockedByTicketId;
	}
}
