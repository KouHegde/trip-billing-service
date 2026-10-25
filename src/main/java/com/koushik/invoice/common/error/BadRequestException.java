package com.koushik.invoice.common.error;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

	private final List<String> errors;

	public BadRequestException(String message) {
		this(message, List.of());
	}

	public BadRequestException(String message, List<String> errors) {
		super(HttpStatus.BAD_REQUEST, message);
		this.errors = List.copyOf(errors);
	}

	@Override
	public Map<String, Object> properties() {
		return errors.isEmpty() ? Map.of() : Map.of("errors", errors);
	}

}
