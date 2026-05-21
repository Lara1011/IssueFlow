package com.att.tdp.issueflow.security;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TokenDenyListService {

	private final Set<String> deniedTokens = ConcurrentHashMap.newKeySet();

	public void deny(String token) {
		deniedTokens.add(token);
	}

	public boolean isDenied(String token) {
		return deniedTokens.contains(token);
	}
}
