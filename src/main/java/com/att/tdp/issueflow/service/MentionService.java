package com.att.tdp.issueflow.service;

import com.att.tdp.issueflow.dto.CommentResponse;
import com.att.tdp.issueflow.dto.MentionedUserResponse;
import com.att.tdp.issueflow.dto.PagedMentionResponse;
import com.att.tdp.issueflow.entity.Comment;
import com.att.tdp.issueflow.entity.CommentMention;
import com.att.tdp.issueflow.entity.User;
import com.att.tdp.issueflow.exception.ResourceNotFoundException;
import com.att.tdp.issueflow.repository.CommentMentionRepository;
import com.att.tdp.issueflow.repository.CommentRepository;
import com.att.tdp.issueflow.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MentionService {

	private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_]+)");
	private static final int DEFAULT_PAGE = 1;
	private static final int DEFAULT_PAGE_SIZE = 20;

	private final CommentMentionRepository commentMentionRepository;
	private final CommentRepository commentRepository;
	private final UserRepository userRepository;

	public MentionService(
		CommentMentionRepository commentMentionRepository,
		CommentRepository commentRepository,
		UserRepository userRepository
	) {
		this.commentMentionRepository = commentMentionRepository;
		this.commentRepository = commentRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public void syncMentionsForComment(Comment comment) {
		deleteMentionsForComment(comment.getId());
		List<CommentMention> mentions = parseMentionedUsers(comment.getContent())
			.stream()
			.map(user -> toMention(comment, user))
			.toList();
		commentMentionRepository.saveAll(mentions);
	}

	@Transactional
	public void deleteMentionsForComment(Long commentId) {
		commentMentionRepository.deleteAllByCommentId(commentId);
	}

	@Transactional(readOnly = true)
	public CommentResponse toCommentResponse(Comment comment) {
		return toCommentResponse(comment, findMentionedUsers(List.of(comment)));
	}

	@Transactional(readOnly = true)
	public List<CommentResponse> toCommentResponses(List<Comment> comments) {
		Map<Long, List<MentionedUserResponse>> mentionedUsersByCommentId = findMentionedUsers(comments);
		return comments.stream()
			.map(comment -> toCommentResponse(comment, mentionedUsersByCommentId))
			.toList();
	}

	@Transactional(readOnly = true)
	public PagedMentionResponse getMentionsForUser(Long userId, Integer page, Integer pageSize) {
		if (!userRepository.existsById(userId)) {
			throw new ResourceNotFoundException("User not found: " + userId);
		}

		int resolvedPage = resolvePage(page);
		int resolvedPageSize = resolvePageSize(pageSize);
		Page<Comment> comments = commentRepository.findCommentsMentioningUser(
			userId,
			PageRequest.of(resolvedPage - 1, resolvedPageSize)
		);

		return new PagedMentionResponse(
			toCommentResponses(comments.getContent()),
			comments.getTotalElements(),
			resolvedPage
		);
	}

	private List<User> parseMentionedUsers(String content) {
		Set<String> usernames = extractLowercaseUsernames(content);
		if (usernames.isEmpty()) {
			return List.of();
		}
		return userRepository.findAllByLowercaseUsernameIn(usernames);
	}

	private Set<String> extractLowercaseUsernames(String content) {
		Set<String> usernames = new LinkedHashSet<>();
		Matcher matcher = MENTION_PATTERN.matcher(content);
		while (matcher.find()) {
			usernames.add(matcher.group(1).toLowerCase(Locale.ROOT));
		}
		return usernames;
	}

	private CommentMention toMention(Comment comment, User user) {
		CommentMention mention = new CommentMention();
		mention.setCommentId(comment.getId());
		mention.setMentionedUserId(user.getId());
		mention.setTicketId(comment.getTicketId());
		return mention;
	}

	private Map<Long, List<MentionedUserResponse>> findMentionedUsers(List<Comment> comments) {
		if (comments.isEmpty()) {
			return Map.of();
		}

		List<Long> commentIds = comments.stream()
			.map(Comment::getId)
			.toList();
		List<CommentMention> mentions = commentMentionRepository.findAllByCommentIdIn(commentIds);
		if (mentions.isEmpty()) {
			return Map.of();
		}

		Map<Long, User> usersById = userRepository.findAllById(mentionedUserIds(mentions))
			.stream()
			.collect(Collectors.toMap(User::getId, Function.identity()));

		return mentions.stream()
			.collect(Collectors.groupingBy(
				CommentMention::getCommentId,
				Collectors.collectingAndThen(
					Collectors.toList(),
					commentMentions -> toMentionedUserResponses(commentMentions, usersById)
				)
			));
	}

	private Collection<Long> mentionedUserIds(List<CommentMention> mentions) {
		return mentions.stream()
			.map(CommentMention::getMentionedUserId)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private List<MentionedUserResponse> toMentionedUserResponses(
		List<CommentMention> mentions,
		Map<Long, User> usersById
	) {
		return mentions.stream()
			.map(mention -> usersById.get(mention.getMentionedUserId()))
			.filter(user -> user != null)
			.map(user -> new MentionedUserResponse(user.getId(), user.getUsername(), user.getFullName()))
			.sorted(Comparator.comparing(MentionedUserResponse::username))
			.collect(Collectors.toCollection(ArrayList::new));
	}

	private CommentResponse toCommentResponse(
		Comment comment,
		Map<Long, List<MentionedUserResponse>> mentionedUsersByCommentId
	) {
		return new CommentResponse(
			comment.getId(),
			comment.getTicketId(),
			comment.getAuthorId(),
			comment.getContent(),
			mentionedUsersByCommentId.getOrDefault(comment.getId(), List.of())
		);
	}

	private int resolvePage(Integer page) {
		if (page == null) {
			return DEFAULT_PAGE;
		}
		if (page < 1) {
			throw new IllegalArgumentException("page must be greater than or equal to 1");
		}
		return page;
	}

	private int resolvePageSize(Integer pageSize) {
		if (pageSize == null) {
			return DEFAULT_PAGE_SIZE;
		}
		if (pageSize < 1) {
			throw new IllegalArgumentException("pageSize must be greater than or equal to 1");
		}
		return pageSize;
	}
}
