package com.att.tdp.issueflow.dto;

import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import java.time.Instant;

public record AuditLogResponse(
	Long id,
	AuditAction action,
	AuditEntityType entityType,
	Long entityId,
	Long performedBy,
	AuditActor actor,
	Instant timestamp
) {
}
