package com.att.tdp.issueflow.repository;

import com.att.tdp.issueflow.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
