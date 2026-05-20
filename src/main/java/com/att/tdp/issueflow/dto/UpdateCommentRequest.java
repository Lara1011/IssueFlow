package com.att.tdp.issueflow.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
	@NotBlank String content
) {
}
