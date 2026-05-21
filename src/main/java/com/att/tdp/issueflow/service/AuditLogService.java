package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AuditLogResponse;
import com.att.tdp.issueflow.entity.AuditLog;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

	private final AuditLogRepository auditLogRepository;

	public AuditLogService(AuditLogRepository auditLogRepository) {
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional(readOnly = true)
	public List<AuditLogResponse> getAuditLogs(
		AuditEntityType entityType,
		Long entityId,
		AuditAction action,
		AuditActor actor
	) {
		return auditLogRepository.findAllFiltered(entityType, entityId, action, actor)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public void record(
		AuditAction action,
		AuditEntityType entityType,
		Long entityId,
		Long performedBy,
		AuditActor actor
	) {
		AuditLog auditLog = new AuditLog();
		auditLog.setAction(action);
		auditLog.setEntityType(entityType);
		auditLog.setEntityId(entityId);
		auditLog.setPerformedBy(performedBy);
		auditLog.setActor(actor);
		auditLog.setTimestamp(Instant.now());
		auditLogRepository.save(auditLog);
	}

	private AuditLogResponse toResponse(AuditLog auditLog) {
		return new AuditLogResponse(
			auditLog.getId(),
			auditLog.getAction(),
			auditLog.getEntityType(),
			auditLog.getEntityId(),
			auditLog.getPerformedBy(),
			auditLog.getActor(),
			auditLog.getTimestamp()
		);
	}
}
