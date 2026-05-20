package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
