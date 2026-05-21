package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.ImportErrorResponse;
import com.att.tdp.issueflow.dto.ImportTicketsResponse;
import com.att.tdp.issueflow.entity.Ticket;
import com.att.tdp.issueflow.enums.AuditAction;
import com.att.tdp.issueflow.enums.AuditActor;
import com.att.tdp.issueflow.enums.AuditEntityType;
import com.att.tdp.issueflow.enums.TicketPriority;
import com.att.tdp.issueflow.enums.TicketStatus;
import com.att.tdp.issueflow.enums.TicketType;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.ProjectRepository;
import com.att.tdp.issueflow.repository.TicketRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TicketCsvService {

	private static final String ID = "id";
	private static final String TITLE = "title";
	private static final String DESCRIPTION = "description";
	private static final String STATUS = "status";
	private static final String PRIORITY = "priority";
	private static final String TYPE = "type";
	private static final String ASSIGNEE_ID = "assigneeId";
	private static final List<String> EXPORT_HEADERS = List.of(
		ID,
		TITLE,
		DESCRIPTION,
		STATUS,
		PRIORITY,
		TYPE,
		ASSIGNEE_ID
	);
	private static final Set<String> REQUIRED_IMPORT_HEADERS = Set.of(TITLE, STATUS, PRIORITY, TYPE);

	private final TicketRepository ticketRepository;
	private final ProjectRepository projectRepository;
	private final UserRepository userRepository;
	private final AuditLogService auditLogService;

	public TicketCsvService(
		TicketRepository ticketRepository,
		ProjectRepository projectRepository,
		UserRepository userRepository,
		AuditLogService auditLogService
	) {
		this.ticketRepository = ticketRepository;
		this.projectRepository = projectRepository;
		this.userRepository = userRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public String exportTickets(Long projectId) {
		validateActiveProject(projectId);
		List<Ticket> tickets = ticketRepository.findAllByProjectIdAndDeletedAtIsNullOrderByIdAsc(projectId);

		try (StringWriter writer = new StringWriter();
			CSVPrinter printer = new CSVPrinter(
				writer,
				CSVFormat.DEFAULT.builder()
					.setHeader(EXPORT_HEADERS.toArray(String[]::new))
					.build()
			)) {
			for (Ticket ticket : tickets) {
				printer.printRecord(
					ticket.getId(),
					ticket.getTitle(),
					ticket.getDescription(),
					ticket.getStatus(),
					ticket.getPriority(),
					ticket.getType(),
					ticket.getAssigneeId()
				);
			}
			printer.flush();
			auditLogService.record(AuditAction.EXPORT, AuditEntityType.TICKET, projectId, null, AuditActor.USER);
			return writer.toString();
		}
		catch (IOException exception) {
			throw new IllegalArgumentException("CSV export failed");
		}
	}

	@Transactional
	public ImportTicketsResponse importTickets(MultipartFile file, Long projectId) {
		validateActiveProject(projectId);
		validateImportFile(file);

		List<ImportErrorResponse> errors = new ArrayList<>();
		int created = 0;

		try (InputStreamReader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
			CSVParser parser = CSVFormat.DEFAULT.builder()
				.setHeader()
				.setSkipHeaderRecord(true)
				.setTrim(false)
				.build()
				.parse(reader)) {
			validateImportHeaders(parser);

			for (CSVRecord record : parser) {
				try {
					Ticket ticket = toTicket(record, projectId);
					ticketRepository.save(ticket);
					created++;
				}
				catch (IllegalArgumentException | ResourceNotFoundException exception) {
					errors.add(new ImportErrorResponse(rowNumber(record), exception.getMessage()));
				}
			}
		}
		catch (IOException | IllegalStateException exception) {
			throw new IllegalArgumentException("CSV file is malformed or could not be read");
		}

		auditLogService.record(AuditAction.IMPORT, AuditEntityType.TICKET, projectId, null, AuditActor.USER);
		return new ImportTicketsResponse(created, errors.size(), errors);
	}

	private Ticket toTicket(CSVRecord record, Long projectId) {
		String title = value(record, TITLE);
		if (!StringUtils.hasText(title)) {
			throw new IllegalArgumentException("title must not be blank");
		}

		Ticket ticket = new Ticket();
		ticket.setTitle(title);
		ticket.setDescription(value(record, DESCRIPTION));
		ticket.setStatus(parseEnum(TicketStatus.class, value(record, STATUS), STATUS));
		ticket.setPriority(parseEnum(TicketPriority.class, value(record, PRIORITY), PRIORITY));
		ticket.setType(parseEnum(TicketType.class, value(record, TYPE), TYPE));
		ticket.setAssigneeId(parseAssigneeId(value(record, ASSIGNEE_ID)));
		ticket.setProjectId(projectId);
		return ticket;
	}

	private Long parseAssigneeId(String rawValue) {
		if (!StringUtils.hasText(rawValue)) {
			return null;
		}
		Long assigneeId;
		try {
			assigneeId = Long.valueOf(rawValue.trim());
		}
		catch (NumberFormatException exception) {
			throw new IllegalArgumentException("assigneeId is invalid: " + rawValue);
		}
		if (!userRepository.existsById(assigneeId)) {
			throw new ResourceNotFoundException("Assignee not found: " + assigneeId);
		}
		return assigneeId;
	}

	private <T extends Enum<T>> T parseEnum(Class<T> enumType, String rawValue, String fieldName) {
		if (!StringUtils.hasText(rawValue)) {
			throw new IllegalArgumentException(fieldName + " must be one of: " + acceptedEnumValues(enumType));
		}
		try {
			return Enum.valueOf(enumType, rawValue.trim());
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(fieldName + " must be one of: " + acceptedEnumValues(enumType));
		}
	}

	private void validateActiveProject(Long projectId) {
		if (projectId == null || projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
			throw new ResourceNotFoundException("Project not found: " + projectId);
		}
	}

	private void validateImportFile(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("file is required and must not be empty");
		}
	}

	private void validateImportHeaders(CSVParser parser) {
		Set<String> headers = parser.getHeaderMap().keySet();
		for (String requiredHeader : REQUIRED_IMPORT_HEADERS) {
			if (!headers.contains(requiredHeader)) {
				throw new IllegalArgumentException("CSV is missing required column: " + requiredHeader);
			}
		}
	}

	private String value(CSVRecord record, String fieldName) {
		if (!record.isMapped(fieldName)) {
			return null;
		}
		return record.get(fieldName);
	}

	private int rowNumber(CSVRecord record) {
		return Math.toIntExact(record.getRecordNumber() + 1);
	}

	private <T extends Enum<T>> String acceptedEnumValues(Class<T> enumType) {
		return Arrays.stream(enumType.getEnumConstants())
			.map(Enum::name)
			.collect(Collectors.joining(", "));
	}
}
