package com.att.tdp.issueflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
	name = "comment_mentions",
	uniqueConstraints = @UniqueConstraint(columnNames = {"commentId", "mentionedUserId"})
)
public class CommentMention extends BaseEntity {

	@Column(nullable = false)
	private Long commentId;

	@Column(nullable = false)
	private Long mentionedUserId;

	@Column(nullable = false)
	private Long ticketId;

	public Long getCommentId() {
		return commentId;
	}

	public void setCommentId(Long commentId) {
		this.commentId = commentId;
	}

	public Long getMentionedUserId() {
		return mentionedUserId;
	}

	public void setMentionedUserId(Long mentionedUserId) {
		this.mentionedUserId = mentionedUserId;
	}

	public Long getTicketId() {
		return ticketId;
	}

	public void setTicketId(Long ticketId) {
		this.ticketId = ticketId;
	}
}
