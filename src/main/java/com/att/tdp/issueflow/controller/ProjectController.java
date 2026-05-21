package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.ProjectResponse;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping
	public List<ProjectResponse> getAllProjects() {
		return projectService.getAllProjects();
	}

	@GetMapping("/deleted")
	public List<ProjectResponse> getDeletedProjects() {
		return projectService.getDeletedProjects();
	}

	@GetMapping("/{projectId}")
	public ProjectResponse getProjectById(@PathVariable Long projectId) {
		return projectService.getProjectById(projectId);
	}

	@PostMapping
	public ProjectResponse createProject(@Valid @RequestBody CreateProjectRequest request) {
		return projectService.createProject(request);
	}

	@PatchMapping("/{projectId}")
	public void updateProject(
		@PathVariable Long projectId,
		@RequestBody UpdateProjectRequest request
	) {
		projectService.updateProject(projectId, request);
	}

	@DeleteMapping("/{projectId}")
	public void deleteProject(@PathVariable Long projectId) {
		projectService.deleteProject(projectId);
	}

	@PostMapping("/{projectId}/restore")
	public void restoreProject(@PathVariable Long projectId) {
		projectService.restoreProject(projectId);
	}
}
