package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.TicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

	List<Ticket> findAllByProjectIdAndDeletedAtIsNull(Long projectId);

	List<Ticket> findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(Long projectId);

	List<Ticket> findAllByProjectIdAndDeletedAtIsNotNullOrderByDeletedAtDescIdDesc(Long projectId);

	Optional<Ticket> findByIdAndDeletedAtIsNull(Long id);

	long countByProjectIdAndAssigneeIdAndDeletedAtIsNullAndStatusNot(
		Long projectId,
		Long assigneeId,
		TicketStatus status
	);
}
