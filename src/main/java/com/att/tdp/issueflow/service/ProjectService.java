package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;

	public ProjectService(ProjectRepository projectRepository, UserRepository userRepository) {
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<ProjectResponse> getAllProjects() {
		return projectRepository.findAllByDeletedAtIsNull()
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public ProjectResponse getProjectById(Long projectId) {
		return toResponse(findActiveProject(projectId));
	}

	@Transactional
	public ProjectResponse createProject(CreateProjectRequest request) {
		if (!userRepository.existsById(request.ownerId())) {
			throw new ResourceNotFoundException("Project owner not found: " + request.ownerId());
		}

		Project project = new Project();
		project.setName(request.name());
		project.setDescription(request.description());
		project.setOwnerId(request.ownerId());

		return toResponse(projectRepository.save(project));
	}

	@Transactional
	public void updateProject(Long projectId, UpdateProjectRequest request) {
		validateUpdateRequest(request);

		Project project = findActiveProject(projectId);
		if (request.name() != null) {
			project.setName(request.name());
		}
		if (request.description() != null) {
			project.setDescription(request.description());
		}
	}

	@Transactional
	public void deleteProject(Long projectId) {
		Project project = findActiveProject(projectId);
		project.setDeletedAt(Instant.now());
	}

	private Project findActiveProject(Long projectId) {
		return projectRepository.findByIdAndDeletedAtIsNull(projectId)
			.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
	}

	private void validateUpdateRequest(UpdateProjectRequest request) {
		if (request.name() == null && request.description() == null) {
			throw new IllegalArgumentException("At least one of name or description must be provided");
		}
		if (request.name() != null && !StringUtils.hasText(request.name())) {
			throw new IllegalArgumentException("name must not be blank");
		}
	}

	private ProjectResponse toResponse(Project project) {
		return new ProjectResponse(
			project.getId(),
			project.getName(),
			project.getDescription(),
			project.getOwnerId()
		);
	}
}
