package com.att.tdp.issueflow.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
		MethodArgumentTypeMismatchException exception,
		HttpServletRequest request
	) {
		String message = resolveTypeMismatchMessage(exception);
		return buildResponse(HttpStatus.BAD_REQUEST, message, request, Map.of());
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
		MissingServletRequestParameterException exception,
		HttpServletRequest request
	) {
		String message = exception.getParameterName() + " is required";
		return buildResponse(HttpStatus.BAD_REQUEST, message, request, Map.of());
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestPart(
		MissingServletRequestPartException exception,
		HttpServletRequest request
	) {
		String message = exception.getRequestPartName() + " is required";
		return buildResponse(HttpStatus.BAD_REQUEST, message, request, Map.of());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(
		MaxUploadSizeExceededException exception,
		HttpServletRequest request
	) {
		return buildResponse(HttpStatus.BAD_REQUEST, "file must be 10 MB or smaller", request, Map.of());
	}

	@ExceptionHandler(BusinessRuleException.class)
	public ResponseEntity<ApiErrorResponse> handleBusinessRule(
		BusinessRuleException exception,
		HttpServletRequest request
	) {
		return buildResponse(HttpStatus.BAD_REQUEST, exception.getMessage(), request, Map.of());
	}

	@ExceptionHandler({
		ObjectOptimisticLockingFailureException.class,
		OptimisticLockingFailureException.class,
		OptimisticLockException.class
	})
	public ResponseEntity<ApiErrorResponse> handleOptimisticLockingFailure(
		Exception exception,
		HttpServletRequest request
	) {
		String message = "Ticket was updated by another request. Please reload and try again.";
		return buildResponse(HttpStatus.CONFLICT, message, request, Map.of());
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
			&& invalidFormatException.getTargetType().isEnum()
			&& invalidFormatException.getPath() != null
			&& !invalidFormatException.getPath().isEmpty()) {
			String fieldName = invalidFormatException.getPath()
				.getLast()
				.getFieldName();
			return fieldName + " must be one of: " + acceptedEnumValues(invalidFormatException.getTargetType());
		}

		return "Request body is invalid";
	}

	private String resolveTypeMismatchMessage(MethodArgumentTypeMismatchException exception) {
		Class<?> requiredType = exception.getRequiredType();
		if (requiredType != null && requiredType.isEnum()) {
			return exception.getName() + " must be one of: " + acceptedEnumValues(requiredType);
		}
		return exception.getName() + " is invalid";
	}

	@SuppressWarnings("unchecked")
	private String acceptedEnumValues(Class<?> targetType) {
		Class<? extends Enum<?>> enumType = (Class<? extends Enum<?>>) targetType;
		return Arrays.stream(enumType.getEnumConstants())
			.map(Enum::name)
			.collect(Collectors.joining(", "));
	}
}
