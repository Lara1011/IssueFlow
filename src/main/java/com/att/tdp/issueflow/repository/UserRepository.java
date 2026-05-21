package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.User;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	@Query("select user from User user where lower(user.username) in :usernames")
	List<User> findAllByLowercaseUsernameIn(@Param("usernames") Collection<String> usernames);
}
