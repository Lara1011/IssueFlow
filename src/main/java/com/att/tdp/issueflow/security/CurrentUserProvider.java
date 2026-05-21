package com.att.tdp.issueflow.security;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

	public Optional<AuthenticatedUser> currentUser() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
			return Optional.empty();
		}
		return Optional.of(authenticatedUser);
	}

	public Long currentUserIdOrNull() {
		return currentUser()
			.map(AuthenticatedUser::id)
			.orElse(null);
	}
}
