package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.CreateTicketRequest;
import com.att.tdp.issueflow.dto.TicketResponse;
import com.att.tdp.issueflow.dto.UpdateTicketRequest;
import com.att.tdp.issueflow.service.TicketService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets")
public class TicketController {

	private final TicketService ticketService;

	public TicketController(TicketService ticketService) {
		this.ticketService = ticketService;
	}

	@GetMapping
	public List<TicketResponse> getTicketsByProject(@RequestParam Long projectId) {
		return ticketService.getTicketsByProject(projectId);
	}

	@GetMapping("/{ticketId}")
	public TicketResponse getTicketById(@PathVariable Long ticketId) {
		return ticketService.getTicketById(ticketId);
	}

	@PostMapping
	public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
		return ticketService.createTicket(request);
	}

	@PatchMapping("/{ticketId}")
	public void updateTicket(
		@PathVariable Long ticketId,
		@RequestBody UpdateTicketRequest request
	) {
		ticketService.updateTicket(ticketId, request);
	}

	@DeleteMapping("/{ticketId}")
	public void deleteTicket(@PathVariable Long ticketId) {
		ticketService.deleteTicket(ticketId);
	}
}
