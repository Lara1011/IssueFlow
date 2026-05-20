package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {
}
