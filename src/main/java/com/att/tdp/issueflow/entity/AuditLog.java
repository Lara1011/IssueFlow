package com.att.tdp.issueflow.entity;

import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditEntityType entityType;

	@Column(nullable = false)
	private Long entityId;

	private Long performedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditActor actor;

	@Column(nullable = false)
	private Instant timestamp;

	public AuditAction getAction() {
		return action;
	}

	public void setAction(AuditAction action) {
		this.action = action;
	}

	public AuditEntityType getEntityType() {
		return entityType;
	}

	public void setEntityType(AuditEntityType entityType) {
		this.entityType = entityType;
	}

	public Long getEntityId() {
		return entityId;
	}

	public void setEntityId(Long entityId) {
		this.entityId = entityId;
	}

	public Long getPerformedBy() {
		return performedBy;
	}

	public void setPerformedBy(Long performedBy) {
		this.performedBy = performedBy;
	}

	public AuditActor getActor() {
		return actor;
	}

	public void setActor(AuditActor actor) {
		this.actor = actor;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}
}
