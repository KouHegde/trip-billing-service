package com.koushik.invoice.invoicing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.koushik.invoice.transaction.domain.FeeLine;
import com.koushik.invoice.transaction.domain.TaxLine;

/**
 * A transaction as it stands at billing time: the money fields the invoice sums, plus {@code snapshotJson},
 * the full representation frozen onto the invoice line.
 */
public record BillableTransaction(UUID transactionId, String type, @Nullable String feeKind, String externalId,
		Instant occurredAt, String currency, BigDecimal total, List<TaxLine> taxLines, List<FeeLine> feeLines,
		String snapshotJson) {
}
