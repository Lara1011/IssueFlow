package com.att.tdp.issueflow.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.dto.CreateProjectRequest;
import com.att.tdp.issueflow.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.entity.Project;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser
class ProjectControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		projectRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createProjectSuccessfully() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		CreateProjectRequest request = new CreateProjectRequest(
			"Sample Project",
			"A sample project",
			owner.getId()
		);

		mockMvc.perform(post("/projects")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.name").value("Sample Project"))
			.andExpect(jsonPath("$.description").value("A sample project"))
			.andExpect(jsonPath("$.ownerId").value(owner.getId()));
	}

	@Test
	void createProjectFailsWhenOwnerDoesNotExist() throws Exception {
		CreateProjectRequest request = new CreateProjectRequest(
			"Sample Project",
			"A sample project",
			999L
		);

		mockMvc.perform(post("/projects")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project owner not found: 999"));
	}

	@Test
	void getAllProjectsReturnsOnlyNonDeletedProjects() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		saveProject("Visible Project", "Visible", owner.getId(), null);
		saveProject("Deleted Project", "Deleted", owner.getId(), Instant.now());

		mockMvc.perform(get("/projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].name").value("Visible Project"))
			.andExpect(jsonPath("$[*].name", not(contains("Deleted Project"))));
	}

	@Test
	void getProjectById() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", "A sample project", owner.getId(), null);

		mockMvc.perform(get("/projects/{projectId}", project.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(project.getId()))
			.andExpect(jsonPath("$.name").value("Sample Project"))
			.andExpect(jsonPath("$.description").value("A sample project"))
			.andExpect(jsonPath("$.ownerId").value(owner.getId()));
	}

	@Test
	void updateProjectNameAndDescription() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", "A sample project", owner.getId(), null);
		UpdateProjectRequest request = new UpdateProjectRequest("Updated Name", "Updated description");

		mockMvc.perform(patch("/projects/{projectId}", project.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects/{projectId}", project.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Updated Name"))
			.andExpect(jsonPath("$.description").value("Updated description"))
			.andExpect(jsonPath("$.ownerId").value(owner.getId()));
	}

	@Test
	void deleteProjectPerformsSoftDelete() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", "A sample project", owner.getId(), null);

		mockMvc.perform(delete("/projects/{projectId}", project.getId()))
			.andExpect(status().isOk());

		Project deletedProject = projectRepository.findById(project.getId()).orElseThrow();
		org.assertj.core.api.Assertions.assertThat(deletedProject.getDeletedAt()).isNotNull();
	}

	@Test
	void deletedProjectIsNotReturnedByGetAllProjects() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", "A sample project", owner.getId(), null);

		mockMvc.perform(delete("/projects/{projectId}", project.getId()))
			.andExpect(status().isOk());

		mockMvc.perform(get("/projects"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void deletedProjectIsNotReturnedByGetProjectById() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", "A sample project", owner.getId(), Instant.now());

		mockMvc.perform(get("/projects/{projectId}", project.getId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Project not found: " + project.getId()));
	}

	@Test
	void createProjectValidationErrorsAreInformative() throws Exception {
		String request = """
			{
			  "name": " ",
			  "description": "A sample project",
			  "ownerId": null
			}
			""";

		mockMvc.perform(post("/projects")
				.contentType(MediaType.APPLICATION_JSON)
				.content(request))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Validation failed"))
			.andExpect(jsonPath("$.fieldErrors.name").value("name is required"))
			.andExpect(jsonPath("$.fieldErrors.ownerId").value("ownerId is required"));
	}

	@Test
	void updateProjectRequiresAtLeastOneField() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", "A sample project", owner.getId(), null);

		mockMvc.perform(patch("/projects/{projectId}", project.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of())))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("At least one of name or description must be provided"));
	}

	@Test
	void updateProjectRejectsBlankNameWhenProvided() throws Exception {
		User owner = saveUser("owner", "owner@example.com", "Owner User", UserRole.ADMIN);
		Project project = saveProject("Sample Project", "A sample project", owner.getId(), null);
		UpdateProjectRequest request = new UpdateProjectRequest(" ", "Updated description");

		mockMvc.perform(patch("/projects/{projectId}", project.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("name must not be blank"));
	}

	private User saveUser(String username, String email, String fullName, UserRole role) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setFullName(fullName);
		user.setRole(role);
		return userRepository.save(user);
	}

	private Project saveProject(String name, String description, Long ownerId, Instant deletedAt) {
		Project project = new Project();
		project.setName(name);
		project.setDescription(description);
		project.setOwnerId(ownerId);
		project.setDeletedAt(deletedAt);
		return projectRepository.save(project);
	}
}
