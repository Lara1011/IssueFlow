package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.ImportTicketsResponse;
import com.att.tdp.issueflow.service.TicketCsvService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tickets")
public class TicketCsvController {

	private final TicketCsvService ticketCsvService;

	public TicketCsvController(TicketCsvService ticketCsvService) {
		this.ticketCsvService = ticketCsvService;
	}

	@GetMapping("/export")
	public ResponseEntity<String> exportTickets(@RequestParam Long projectId) {
		String csv = ticketCsvService.exportTickets(projectId);
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType("text/csv"))
			.header(
				HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment()
					.filename("tickets-project-" + projectId + ".csv")
					.build()
					.toString()
			)
			.body(csv);
	}

	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ImportTicketsResponse importTickets(
		@RequestParam MultipartFile file,
		@RequestParam Long projectId
	) {
		return ticketCsvService.importTickets(file, projectId);
	}
}
