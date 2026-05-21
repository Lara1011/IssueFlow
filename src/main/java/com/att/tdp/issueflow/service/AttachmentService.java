package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.AttachmentResponse;
import com.att.tdp.issueflow.entity.Attachment;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.AttachmentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.security.CurrentUserProvider;
import java.io.IOException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

	private static final long MAX_FILE_SIZE_BYTES = 10L * 1024L * 1024L;
	private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
		"image/png",
		"image/jpeg",
		"application/pdf",
		"text/plain"
	);

	private final AttachmentRepository attachmentRepository;
	private final TicketRepository ticketRepository;
	private final AuditLogService auditLogService;
	private final CurrentUserProvider currentUserProvider;

	public AttachmentService(
		AttachmentRepository attachmentRepository,
		TicketRepository ticketRepository,
		AuditLogService auditLogService,
		CurrentUserProvider currentUserProvider
	) {
		this.attachmentRepository = attachmentRepository;
		this.ticketRepository = ticketRepository;
		this.auditLogService = auditLogService;
		this.currentUserProvider = currentUserProvider;
	}

	@Transactional
	public AttachmentResponse uploadAttachment(Long ticketId, MultipartFile file) {
		validateActiveTicket(ticketId);
		validateFile(file);

		Attachment attachment = new Attachment();
		attachment.setTicketId(ticketId);
		attachment.setFilename(file.getOriginalFilename());
		attachment.setContentType(file.getContentType());
		attachment.setSizeBytes(file.getSize());
		attachment.setData(readFileBytes(file));

		Attachment savedAttachment = attachmentRepository.save(attachment);
		auditLogService.record(
			AuditAction.UPLOAD_ATTACHMENT,
			AuditEntityType.ATTACHMENT,
			savedAttachment.getId(),
			currentUserProvider.currentUserIdOrNull(),
			AuditActor.USER
		);

		return toResponse(savedAttachment);
	}

	@Transactional
	public void deleteAttachment(Long ticketId, Long attachmentId) {
		validateActiveTicket(ticketId);
		Attachment attachment = attachmentRepository.findById(attachmentId)
			.orElseThrow(() -> new ResourceNotFoundException("Attachment not found: " + attachmentId));
		if (!ticketId.equals(attachment.getTicketId())) {
			throw new ResourceNotFoundException(
				"Attachment " + attachmentId + " does not belong to ticket " + ticketId
			);
		}

		attachmentRepository.delete(attachment);
		auditLogService.record(
			AuditAction.DELETE_ATTACHMENT,
			AuditEntityType.ATTACHMENT,
			attachmentId,
			currentUserProvider.currentUserIdOrNull(),
			AuditActor.USER
		);
	}

	private void validateActiveTicket(Long ticketId) {
		if (ticketRepository.findByIdAndDeletedAtIsNull(ticketId).isEmpty()) {
			throw new ResourceNotFoundException("Ticket not found: " + ticketId);
		}
	}

	private void validateFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("file is required and must not be empty");
		}
		if (file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new IllegalArgumentException("file must be 10 MB or smaller");
		}
		if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
			throw new IllegalArgumentException("Unsupported content type: " + file.getContentType());
		}
	}

	private byte[] readFileBytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("file could not be read");
		}
	}

	private AttachmentResponse toResponse(Attachment attachment) {
		return new AttachmentResponse(
			attachment.getId(),
			attachment.getTicketId(),
			attachment.getFilename(),
			attachment.getContentType()
		);
	}
}
