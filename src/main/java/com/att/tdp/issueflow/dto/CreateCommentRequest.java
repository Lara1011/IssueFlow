package com.att.tdp.issueflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
	@NotNull(message = "authorId is required") Long authorId,
	@NotBlank(message = "content is required") String content
) {
}
