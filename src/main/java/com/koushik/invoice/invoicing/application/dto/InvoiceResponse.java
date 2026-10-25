package com.koushik.invoice.invoicing.application.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.koushik.invoice.transaction.application.dto.FeeLineDto;
import com.koushik.invoice.transaction.application.dto.TaxLineDto;

public record InvoiceResponse(UUID id, String currency, List<InvoiceLineResponse> lines,
		List<TaxLineDto> taxRollup, List<FeeLineDto> feeRollup, InvoiceTotals totals, Instant createdAt,
		Instant updatedAt) {
}
