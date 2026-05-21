package com.att.tdp.issueflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtTokenService jwtTokenService;
	private final TokenDenyListService tokenDenyListService;
	private final JsonAuthenticationEntryPoint authenticationEntryPoint;

	public JwtAuthenticationFilter(
		JwtTokenService jwtTokenService,
		TokenDenyListService tokenDenyListService,
		JsonAuthenticationEntryPoint authenticationEntryPoint
	) {
		this.jwtTokenService = jwtTokenService;
		this.tokenDenyListService = tokenDenyListService;
		this.authenticationEntryPoint = authenticationEntryPoint;
	}

	@Override
	protected void doFilterInternal(
		HttpServletRequest request,
		HttpServletResponse response,
		FilterChain filterChain
	) throws ServletException, IOException {
		String authorization = request.getHeader(SecurityConstants.AUTHORIZATION_HEADER);
		if (authorization == null || authorization.isBlank()) {
			filterChain.doFilter(request, response);
			return;
		}

		if (!authorization.startsWith(SecurityConstants.BEARER_PREFIX)) {
			authenticationEntryPoint.commence(request, response, new BadCredentialsException("Invalid Authorization header"));
			return;
		}

		String token = authorization.substring(SecurityConstants.BEARER_PREFIX.length());
		try {
			if (tokenDenyListService.isDenied(token)) {
				throw new JwtAuthenticationException("Token has been logged out");
			}
			AuthenticatedUser authenticatedUser = jwtTokenService.parseToken(token);
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				authenticatedUser,
				token,
				List.of(new SimpleGrantedAuthority("ROLE_" + authenticatedUser.role().name()))
			);
			SecurityContextHolder.getContext().setAuthentication(authentication);
			filterChain.doFilter(request, response);
		}
		catch (JwtAuthenticationException exception) {
			SecurityContextHolder.clearContext();
			authenticationEntryPoint.commence(request, response, new BadCredentialsException(exception.getMessage()));
		}
	}
}
