package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CommentResponse;
import com.att.tdp.issueflow.dto.CreateCommentRequest;
import com.att.tdp.issueflow.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

	private final CommentRepository commentRepository;
	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;

	public CommentService(
		CommentRepository commentRepository,
		TicketRepository ticketRepository,
		UserRepository userRepository
	) {
		this.commentRepository = commentRepository;
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<CommentResponse> getCommentsForTicket(Long ticketId) {
		validateActiveTicket(ticketId);
		return commentRepository.findAllByTicketId(ticketId)
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public CommentResponse addComment(Long ticketId, CreateCommentRequest request) {
		validateActiveTicket(ticketId);
		validateAuthor(request.authorId());

		Comment comment = new Comment();
		comment.setTicketId(ticketId);
		comment.setAuthorId(request.authorId());
		comment.setContent(request.content());

		return toResponse(commentRepository.save(comment));
	}

	@Transactional
	public void updateComment(Long ticketId, Long commentId, UpdateCommentRequest request) {
		validateActiveTicket(ticketId);
		Comment comment = findCommentForTicket(ticketId, commentId);
		comment.setContent(request.content());
	}

	@Transactional
	public void deleteComment(Long ticketId, Long commentId) {
		validateActiveTicket(ticketId);
		Comment comment = findCommentForTicket(ticketId, commentId);
		commentRepository.delete(comment);
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

	private CommentResponse toResponse(Comment comment) {
		return new CommentResponse(
			comment.getId(),
			comment.getTicketId(),
			comment.getAuthorId(),
			comment.getContent(),
			List.of()
		);
	}
}
