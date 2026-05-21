package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.WorkloadResponse;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.UserRole;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkloadService {

	private final ProjectRepository projectRepository;
	private final TicketRepository ticketRepository;
	private final UserRepository userRepository;

	public WorkloadService(
		ProjectRepository projectRepository,
		TicketRepository ticketRepository,
		UserRepository userRepository
	) {
		this.projectRepository = projectRepository;
		this.ticketRepository = ticketRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<WorkloadResponse> getProjectWorkload(Long projectId) {
		validateActiveProject(projectId);
		return developerWorkloads(projectId);
	}

	@Transactional(readOnly = true)
	public Optional<User> findLeastLoadedDeveloper(Long projectId) {
		return developerWorkloads(projectId)
			.stream()
			.findFirst()
			.flatMap(workload -> userRepository.findById(workload.userId()));
	}

	private List<WorkloadResponse> developerWorkloads(Long projectId) {
		return userRepository.findAllByRoleOrderByIdAsc(UserRole.DEVELOPER)
			.stream()
			.map(user -> toWorkload(projectId, user))
			.sorted(
				Comparator.comparingLong(WorkloadResponse::openTicketCount)
					.thenComparing(WorkloadResponse::userId)
			)
			.toList();
	}

	private WorkloadResponse toWorkload(Long projectId, User user) {
		long openTicketCount = ticketRepository.countByProjectIdAndAssigneeIdAndDeletedAtIsNullAndStatusNot(
			projectId,
			user.getId(),
			TicketStatus.DONE
		);
		return new WorkloadResponse(user.getId(), user.getUsername(), openTicketCount);
	}

	private void validateActiveProject(Long projectId) {
		if (projectId == null || projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
	}
}
