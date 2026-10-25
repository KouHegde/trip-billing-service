package com.koushik.invoice.transaction.application;

import java.util.Map;
import java.util.UUID;

import com.koushik.invoice.common.error.ConflictException;

/** 409 for a change to a transaction that is already billed: the invoice snapshot must stay frozen. */
public class TransactionInvoicedException extends ConflictException {

	private final UUID transactionId;

	public TransactionInvoicedException(UUID transactionId) {
		super("Transaction " + transactionId + " is on an invoice and can no longer be changed or deleted");
		this.transactionId = transactionId;
	}

	@Override
	public Map<String, Object> properties() {
		return Map.of("transaction_id", transactionId);
	}

}
