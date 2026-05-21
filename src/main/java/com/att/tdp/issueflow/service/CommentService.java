package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CommentResponse;
import com.att.tdp.issueflow.dto.CreateCommentRequest;
import com.att.tdp.issueflow.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.CurrentUserProvider;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

	private final CommentRepository commentRepository;
	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;
	private final MentionService mentionService;
	private final AuditLogService auditLogService;
	private final CurrentUserProvider currentUserProvider;

	public CommentService(
		CommentRepository commentRepository,
		TicketRepository ticketRepository,
		UserRepository userRepository,
		MentionService mentionService,
		AuditLogService auditLogService,
		CurrentUserProvider currentUserProvider
	) {
		this.commentRepository = commentRepository;
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
		this.mentionService = mentionService;
		this.auditLogService = auditLogService;
		this.currentUserProvider = currentUserProvider;
	}

	@Transactional(readOnly = true)
	public List<CommentResponse> getCommentsForTicket(Long ticketId) {
		validateActiveTicket(ticketId);
		return mentionService.toCommentResponses(commentRepository.findAllByTicketId(ticketId));
	}

	@Transactional
	public CommentResponse addComment(Long ticketId, CreateCommentRequest request) {
		validateActiveTicket(ticketId);
		validateAuthor(request.authorId());

		Comment comment = new Comment();
		comment.setTicketId(ticketId);
		comment.setAuthorId(request.authorId());
		comment.setContent(request.content());

		Comment savedComment = commentRepository.save(comment);
		mentionService.syncMentionsForComment(savedComment);
		auditLogService.record(
			AuditAction.CREATE,
			AuditEntityType.COMMENT,
			savedComment.getId(),
			savedComment.getAuthorId(),
			AuditActor.USER
		);
		return mentionService.toCommentResponse(savedComment);
	}

	@Transactional
	public void updateComment(Long ticketId, Long commentId, UpdateCommentRequest request) {
		validateActiveTicket(ticketId);
		Comment comment = findCommentForTicket(ticketId, commentId);
		comment.setContent(request.content());
		mentionService.syncMentionsForComment(comment);
		auditLogService.record(
			AuditAction.UPDATE,
			AuditEntityType.COMMENT,
			comment.getId(),
			currentUserProvider.currentUserIdOrNull(),
			AuditActor.USER
		);
	}

	@Transactional
	public void deleteComment(Long ticketId, Long commentId) {
		validateActiveTicket(ticketId);
		Comment comment = findCommentForTicket(ticketId, commentId);
		Long authorId = comment.getAuthorId();
		mentionService.deleteMentionsForComment(comment.getId());
		commentRepository.delete(comment);
		auditLogService.record(
			AuditAction.DELETE,
			AuditEntityType.COMMENT,
			commentId,
			currentUserProvider.currentUser()
				.map(user -> user.id())
				.orElse(authorId),
			AuditActor.USER
		);
	}

	private void validateActiveTicket(Long ticketId) {
		if (ticketRepository.findByIdAndDeletedAtIsNull(ticketId).isEmpty()) {
			throw new ResourceNotFoundException("Ticket not found: " + ticketId);
		}
	}

	private void validateAuthor(Long authorId) {
		if (!userRepository.existsById(authorId)) {
			throw new ResourceNotFoundException("Author not found: " + authorId);
		}
	}

	private Comment findCommentForTicket(Long ticketId, Long commentId) {
		Comment comment = commentRepository.findById(commentId)
			.orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));
		if (!ticketId.equals(comment.getTicketId())) {
			throw new ResourceNotFoundException("Comment " + commentId + " does not belong to ticket " + ticketId);
		}
		return comment;
	}
}
