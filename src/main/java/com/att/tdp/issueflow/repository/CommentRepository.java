package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Comment;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	List<Comment> findAllByTicketId(Long ticketId);

	@Query(
		value = """
			select comment
			from Comment comment
			join CommentMention mention on mention.commentId = comment.id
			where mention.mentionedUserId = :userId
			order by comment.createdAt desc, comment.id desc
			""",
		countQuery = """
			select count(comment)
			from Comment comment
			join CommentMention mention on mention.commentId = comment.id
			where mention.mentionedUserId = :userId
			"""
	)
	Page<Comment> findCommentsMentioningUser(@Param("userId") Long userId, Pageable pageable);
}
