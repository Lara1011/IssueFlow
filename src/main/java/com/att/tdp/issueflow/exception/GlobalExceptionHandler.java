package com.att.tdp.issueflow.exception;

import com.att.tdp.issueflow.enums.UserRole;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNotFound(
		ResourceNotFoundException exception,
		HttpServletRequest request
	) {
		return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(
		MethodArgumentNotValidException exception,
		HttpServletRequest request
	) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors().forEach(error ->
			fieldErrors.put(error.getField(), error.getDefaultMessage())
		);

		return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
		HttpMessageNotReadableException exception,
		HttpServletRequest request
	) {
		String message = resolveUnreadableBodyMessage(exception);
		return buildResponse(HttpStatus.BAD_REQUEST, message, request, Map.of());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
		IllegalArgumentException exception,
		HttpServletRequest request
	) {
		return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpected(
		Exception exception,
		HttpServletRequest request
	) {
		return buildResponse(
			HttpStatus.INTERNAL_SERVER_ERROR,
			"Unexpected server error",
			request,
			Map.of()
		);
	}

	private ResponseEntity<ApiErrorResponse> buildResponse(
		HttpStatus status,
		String message,
		HttpServletRequest request,
		Map<String, String> fieldErrors
	) {
		ApiErrorResponse response = new ApiErrorResponse(
			Instant.now(),
			status.value(),
			status.getReasonPhrase(),
			message,
			request.getRequestURI(),
			fieldErrors
		);

		return ResponseEntity.status(status).body(response);
	}

	private String resolveUnreadableBodyMessage(HttpMessageNotReadableException exception) {
		Throwable cause = exception.getCause();
		if (cause instanceof InvalidFormatException invalidFormatException
			&& invalidFormatException.getTargetType() == UserRole.class
			&& invalidFormatException.getPath() != null
			&& invalidFormatException.getPath().stream().anyMatch(reference -> "role".equals(reference.getFieldName()))) {
			return "role must be one of: " + acceptedValues(UserRole.class);
		}

		return "Request body is invalid";
	}

	private String acceptedValues(Class<? extends Enum<?>> enumType) {
		return Arrays.stream(enumType.getEnumConstants())
			.map(Enum::name)
			.collect(Collectors.joining(", "));
	}
}
