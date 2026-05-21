package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.AddDependencyRequest;
import com.att.tdp.issueflow.dto.TicketDependencyResponse;
import com.att.tdp.issueflow.service.TicketDependencyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets/{ticketId}/dependencies")
public class TicketDependencyController {

	private final TicketDependencyService ticketDependencyService;

	public TicketDependencyController(TicketDependencyService ticketDependencyService) {
		this.ticketDependencyService = ticketDependencyService;
	}

	@PostMapping
	public void addDependency(
		@PathVariable Long ticketId,
		@Valid @RequestBody AddDependencyRequest request
	) {
		ticketDependencyService.addDependency(ticketId, request);
	}

	@GetMapping
	public List<TicketDependencyResponse> getDependencies(@PathVariable Long ticketId) {
		return ticketDependencyService.getDependencies(ticketId);
	}

	@DeleteMapping("/{blockerId}")
	public void removeDependency(
		@PathVariable Long ticketId,
		@PathVariable Long blockerId
	) {
		ticketDependencyService.removeDependency(ticketId, blockerId);
	}
}
