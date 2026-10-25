package com.koushik.invoice.common.error;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

	private final List<String> missingIds;

	public NotFoundException(String resource, Collection<?> missingIds) {
		super(HttpStatus.NOT_FOUND, resource + " not found: " + missingIds);
		this.missingIds = missingIds.stream().map(String::valueOf).toList();
	}

	public NotFoundException(String resource, Object id) {
		this(resource, List.of(id));
	}

	@Override
	public Map<String, Object> properties() {
		return Map.of("missing_ids", missingIds);
	}

}
