package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** The fields every transaction type shares, as accepted on create. */
public record TransactionHeader(String externalId, String currency, Instant occurredAt, BigDecimal total,
		List<TaxLine> taxLines, List<FeeLine> feeLines) {
}
