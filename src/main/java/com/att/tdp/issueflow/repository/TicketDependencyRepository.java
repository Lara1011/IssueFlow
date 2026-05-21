package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.TicketDependency;
import com.att.tdp.issueflow.enums.TicketStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketDependencyRepository extends JpaRepository<TicketDependency, Long> {

	List<TicketDependency> findAllByTicketId(Long ticketId);

	Optional<TicketDependency> findByTicketIdAndBlockedByTicketId(Long ticketId, Long blockedByTicketId);

	boolean existsByTicketIdAndBlockedByTicketId(Long ticketId, Long blockedByTicketId);

	@Query("""
		select count(blocker) > 0
		from TicketDependency dependency
		join Ticket blocker on blocker.id = dependency.blockedByTicketId
		where dependency.ticketId = :ticketId
			and blocker.deletedAt is null
			and blocker.status <> :resolvedStatus
		""")
	boolean hasUnresolvedBlockers(
		@Param("ticketId") Long ticketId,
		@Param("resolvedStatus") TicketStatus resolvedStatus
	);
}
