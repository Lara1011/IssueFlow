package com.att.tdp.issueflow.controller;

import com.att.tdp.issueflow.dto.PagedMentionResponse;
import com.att.tdp.issueflow.service.MentionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users/{userId}/mentions")
public class MentionController {

	private final MentionService mentionService;

	public MentionController(MentionService mentionService) {
		this.mentionService = mentionService;
	}

	@GetMapping
	public PagedMentionResponse getMentionsForUser(
		@PathVariable Long userId,
		@RequestParam(required = false) Integer page,
		@RequestParam(required = false) Integer pageSize
	) {
		return mentionService.getMentionsForUser(userId, page, pageSize);
	}
}
