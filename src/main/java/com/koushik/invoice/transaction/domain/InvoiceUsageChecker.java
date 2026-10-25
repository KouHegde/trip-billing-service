package com.koushik.invoice.transaction.domain;

import java.util.UUID;

/**
 * Port: answers whether a transaction is billed on any invoice. Declared here and implemented by the
 * invoicing module, so the transaction module never depends on invoicing.
 */
public interface InvoiceUsageChecker {

	boolean isInvoiced(UUID transactionId);

}
