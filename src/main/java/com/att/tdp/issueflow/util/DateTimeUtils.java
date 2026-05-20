package com.att.tdp.issueflow.util;

import java.time.Instant;

public final class DateTimeUtils {

	private DateTimeUtils() {
	}

	public static boolean isPast(Instant value, Instant now) {
		return value != null && value.isBefore(now);
	}
}
