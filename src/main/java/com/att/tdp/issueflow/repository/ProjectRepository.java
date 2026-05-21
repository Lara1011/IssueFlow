package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Project;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	List<Project> findAllByDeletedAtIsNull();

	List<Project> findAllByDeletedAtIsNotNullOrderByDeletedAtDescIdDesc();

	Optional<Project> findByIdAndDeletedAtIsNull(Long id);
}
