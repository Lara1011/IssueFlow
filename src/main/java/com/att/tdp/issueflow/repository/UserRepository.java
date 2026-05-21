package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.UserRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	Optional<User> findByUsername(String username);

	List<User> findAllByRoleOrderByIdAsc(UserRole role);

	@Query("select user from User user where lower(user.username) in :usernames")
	List<User> findAllByLowercaseUsernameIn(@Param("usernames") Collection<String> usernames);
}
