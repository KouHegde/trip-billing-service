package com.koushik.invoice.transaction.application;

import java.util.Map;
import java.util.UUID;

import com.koushik.invoice.common.error.ConflictException;

/** 409 for a second create with a known {@code external_id}; carries the id of the row that already exists. */
public class DuplicateTransactionException extends ConflictException {

	private final String externalId;

	private final UUID existingId;

	public DuplicateTransactionException(String externalId, UUID existingId) {
		super("A transaction with external_id '" + externalId + "' already exists");
		this.externalId = externalId;
		this.existingId = existingId;
	}

	@Override
	public Map<String, Object> properties() {
		return Map.of("existing_id", existingId, "external_id", externalId);
	}

}
