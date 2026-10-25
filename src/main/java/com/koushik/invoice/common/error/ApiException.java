package com.koushik.invoice.common.error;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Base for every error the API reports on purpose. {@link GlobalExceptionHandler} turns it into an
 * RFC 9457 problem body; {@link #properties()} become extra members of that body.
 */
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;

	protected ApiException(HttpStatus status, String message) {
		super(message);
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}

	public Map<String, Object> properties() {
		return Map.of();
	}

}
