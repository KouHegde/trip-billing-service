package com.koushik.invoice.common;

import java.util.Optional;
import java.util.UUID;

/** Ids arrive as strings; anything that is not a UUID cannot exist, so callers treat it as not found. */
public final class Ids {

	private Ids() {
	}

	public static Optional<UUID> parse(String raw) {
		if (raw == null || raw.length() != 36) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(raw));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
