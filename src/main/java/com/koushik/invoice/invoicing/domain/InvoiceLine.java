package com.koushik.invoice.invoicing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.hibernate.annotations.ColumnTransformer;
import org.jspecify.annotations.Nullable;

/**
 * Frozen copy of one transaction on an invoice (Memento). Money and identity are real columns; everything
 * else lives in the {@code snapshot} JSON, never read back from the live transaction.
 */
@Embeddable
public record InvoiceLine(@Column(name = "transaction_id") UUID transactionId,
		@Column(name = "transaction_type") String transactionType, @Column(name = "fee_kind") @Nullable String feeKind,
		@Column(name = "external_id") String externalId, @Column(name = "occurred_at") Instant occurredAt,
		@Column(name = "currency") String currency, @Column(name = "total") BigDecimal total,
		@Column(name = "snapshot", columnDefinition = "jsonb") @ColumnTransformer(write = "?::jsonb") String snapshot) {

	static InvoiceLine of(BillableTransaction transaction) {
		return new InvoiceLine(transaction.transactionId(), transaction.type(), transaction.feeKind(),
				transaction.externalId(), transaction.occurredAt(), transaction.currency(), transaction.total(),
				transaction.snapshotJson());
	}

}
