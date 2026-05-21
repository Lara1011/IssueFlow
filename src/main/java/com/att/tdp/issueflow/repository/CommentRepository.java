package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

	List<Comment> findAllByTicketId(Long ticketId);
}
