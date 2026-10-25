package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import com.koushik.invoice.common.validation.Money;

/**
 * Aggregate root for a booked transaction. Shared header columns live in {@code transactions}; each subtype
 * adds its own table (JPA {@code JOINED} inheritance) so type-specific fields are real, constrained columns.
 */
@Entity
@Table(name = "transactions")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "type", length = 16)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Transaction {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	/** Client's booking reference: the business identity, unique in the database. Never changes after create. */
	@Column(name = "external_id", nullable = false, updatable = false)
	private String externalId;

	/** Immutable: an invoice's single-currency rule must hold for every transaction it may ever bill. */
	@Column(nullable = false, updatable = false)
	private String currency;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(nullable = false)
	private BigDecimal total;

	@ElementCollection
	@CollectionTable(name = "transaction_tax_lines", joinColumns = @JoinColumn(name = "transaction_id"))
	@OrderColumn(name = "line_no")
	@Getter(AccessLevel.NONE)
	private List<TaxLine> taxLines = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "transaction_fee_lines", joinColumns = @JoinColumn(name = "transaction_id"))
	@OrderColumn(name = "line_no")
	@Getter(AccessLevel.NONE)
	private List<FeeLine> feeLines = new ArrayList<>();

	/**
	 * Optimistic-lock backstop. Writers already serialise on a row lock ({@code SELECT ... FOR UPDATE}); if a
	 * code path ever skipped it, a stale write fails here instead of silently overwriting.
	 */
	@Version
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Transaction(TransactionHeader header) {
		this.externalId = header.externalId();
		this.currency = header.currency();
		this.occurredAt = header.occurredAt();
		this.total = Money.normalize(header.total());
		this.taxLines.addAll(header.taxLines());
		this.feeLines.addAll(header.feeLines());
	}

	public abstract TransactionType getType();

	public List<TaxLine> getTaxLines() {
		return List.copyOf(taxLines);
	}

	public List<FeeLine> getFeeLines() {
		return List.copyOf(feeLines);
	}

	public void changeOccurredAt(Instant occurredAt) {
		this.occurredAt = occurredAt;
	}

	public void changeTotal(BigDecimal total) {
		this.total = Money.normalize(total);
	}

	/** Replaces the whole list; flushed as delete + insert inside the caller's DB transaction. */
	public void replaceTaxLines(List<TaxLine> lines) {
		taxLines.clear();
		taxLines.addAll(lines);
	}

	public void replaceFeeLines(List<FeeLine> lines) {
		feeLines.clear();
		feeLines.addAll(lines);
	}

}
