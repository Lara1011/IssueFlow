package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.CommentMention;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentMentionRepository extends JpaRepository<CommentMention, Long> {
}
