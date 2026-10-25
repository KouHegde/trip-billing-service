package com.koushik.invoice.invoicing.infrastructure;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.koushik.invoice.invoicing.domain.InvoiceRepository;
import com.koushik.invoice.transaction.domain.InvoiceUsageChecker;

/** Implements the transaction module's port, keeping the dependency direction invoicing → transaction. */
@Component
class InvoiceUsageCheckerAdapter implements InvoiceUsageChecker {

	private final InvoiceRepository invoices;

	InvoiceUsageCheckerAdapter(InvoiceRepository invoices) {
		this.invoices = invoices;
	}

	@Override
	public boolean isInvoiced(UUID transactionId) {
		return invoices.existsLineForTransaction(transactionId);
	}

}
