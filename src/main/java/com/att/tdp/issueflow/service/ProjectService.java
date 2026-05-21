package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.dto.WorkloadResponse;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.att.tdp.issueflow.security.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ProjectService {

	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;
	private final AuditLogService auditLogService;
	private final WorkloadService workloadService;
	private final CurrentUserProvider currentUserProvider;

	public ProjectService(
		ProjectRepository projectRepository,
		UserRepository userRepository,
		AuditLogService auditLogService,
		WorkloadService workloadService,
		CurrentUserProvider currentUserProvider
	) {
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
		this.workloadService = workloadService;
		this.currentUserProvider = currentUserProvider;
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

	@Transactional(readOnly = true)
	public List<ProjectResponse> getDeletedProjects() {
		return projectRepository.findAllByDeletedAtIsNotNullOrderByDeletedAtDescIdDesc()
			.stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<WorkloadResponse> getProjectWorkload(Long projectId) {
		return workloadService.getProjectWorkload(projectId);
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

		Project savedProject = projectRepository.save(project);
		auditLogService.record(
			AuditAction.CREATE,
			AuditEntityType.PROJECT,
			savedProject.getId(),
			currentUserProvider.currentUserIdOrNull(),
			AuditActor.USER
		);
		return toResponse(savedProject);
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
		auditLogService.record(
			AuditAction.UPDATE,
			AuditEntityType.PROJECT,
			project.getId(),
			currentUserProvider.currentUserIdOrNull(),
			AuditActor.USER
		);
	}

	@Transactional
	public void deleteProject(Long projectId) {
		Project project = findActiveProject(projectId);
		project.setDeletedAt(Instant.now());
		auditLogService.record(
			AuditAction.DELETE,
			AuditEntityType.PROJECT,
			project.getId(),
			currentUserProvider.currentUserIdOrNull(),
			AuditActor.USER
		);
	}

	@Transactional
	public void restoreProject(Long projectId) {
		Project project = findProject(projectId);
		if (project.getDeletedAt() == null) {
			return;
		}
		project.setDeletedAt(null);
		auditLogService.record(
			AuditAction.RESTORE,
			AuditEntityType.PROJECT,
			project.getId(),
			currentUserProvider.currentUserIdOrNull(),
			AuditActor.USER
		);
	}

	private Project findProject(Long projectId) {
		return projectRepository.findById(projectId)
			.orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));
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
