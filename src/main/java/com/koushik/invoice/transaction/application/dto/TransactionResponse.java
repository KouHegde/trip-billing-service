package com.koushik.invoice.transaction.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.koushik.invoice.transaction.application.dto.metadata.TransactionMetadata;
import com.koushik.invoice.transaction.domain.TransactionType;

/** Stored transaction as returned by the API; also the frozen snapshot copied onto invoice lines. */
public record TransactionResponse(UUID id, TransactionType type, String externalId, String currency,
		Instant occurredAt, BigDecimal total, List<TaxLineDto> taxLines, List<FeeLineDto> feeLines,
		TransactionMetadata metadata, Instant createdAt, Instant updatedAt) {
}
