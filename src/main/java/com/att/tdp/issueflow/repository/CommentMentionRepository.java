package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.CommentMention;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {

	List<CommentMention> findAllByCommentId(Long commentId);

	List<CommentMention> findAllByCommentIdIn(Collection<Long> commentIds);

	@Modifying
	@Query("delete from CommentMention mention where mention.commentId = :commentId")
	void deleteAllByCommentId(@Param("commentId") Long commentId);
}
