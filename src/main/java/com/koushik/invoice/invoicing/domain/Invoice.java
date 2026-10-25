package com.koushik.invoice.invoicing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

import com.koushik.invoice.common.error.BadRequestException;
import com.koushik.invoice.common.error.ConflictException;
import com.koushik.invoice.common.validation.Money;
import com.koushik.invoice.transaction.domain.FeeLine;
import com.koushik.invoice.transaction.domain.TaxLine;

/**
 * Invoice aggregate. Built only through {@link #issue} / {@link #reissue}, which enforce its invariants: at
 * least one transaction, no transaction twice, a single currency, totals and rollups computed from the
 * snapshot lines.
 */
@Entity
@Table(name = "invoices")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invoice {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false)
	private String currency;

	@Column(name = "grand_total", nullable = false)
	private BigDecimal grandTotal;

	@ElementCollection
	@CollectionTable(name = "invoice_lines", joinColumns = @JoinColumn(name = "invoice_id"))
	@OrderColumn(name = "line_no")
	@Getter(AccessLevel.NONE)
	private List<InvoiceLine> lines = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "invoice_tax_rollups", joinColumns = @JoinColumn(name = "invoice_id"))
	@OrderColumn(name = "line_no")
	@Getter(AccessLevel.NONE)
	private List<TaxLine> taxRollup = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "invoice_fee_rollups", joinColumns = @JoinColumn(name = "invoice_id"))
	@OrderColumn(name = "line_no")
	@Getter(AccessLevel.NONE)
	private List<FeeLine> feeRollup = new ArrayList<>();

	/** Backstop for overlapping PATCH/DELETE, which already serialise on the invoice's row lock. */
	@Version
	private long version;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	public static Invoice issue(List<BillableTransaction> transactions) {
		Invoice invoice = new Invoice();
		invoice.snapshot(transactions);
		return invoice;
	}

	/** Replaces every line and rollup from the given transactions' current state. */
	public void reissue(List<BillableTransaction> transactions) {
		snapshot(transactions);
	}

	public List<InvoiceLine> getLines() {
		return List.copyOf(lines);
	}

	public List<TaxLine> getTaxRollup() {
		return List.copyOf(taxRollup);
	}

	public List<FeeLine> getFeeRollup() {
		return List.copyOf(feeRollup);
	}

	private void snapshot(List<BillableTransaction> transactions) {
		if (transactions.isEmpty()) {
			throw new BadRequestException("An invoice needs at least one transaction");
		}
		// Never deduplicate silently: a repeated id is the client's mistake and must not change the total.
		Set<UUID> seen = new HashSet<>();
		for (BillableTransaction transaction : transactions) {
			if (!seen.add(transaction.transactionId())) {
				throw new ConflictException("Transaction " + transaction.transactionId() + " is listed more than once");
			}
		}
		Set<String> currencies = transactions.stream()
			.map(BillableTransaction::currency)
			.collect(Collectors.toCollection(TreeSet::new));
		if (currencies.size() > 1) {
			throw new MixedCurrencyException(currencies);
		}
		// Totals and rollups are computed once here and stored; reads never recompute them from live data.
		// Everything is computed and checked before any field changes, so a rejected reissue leaves this invoice
		// exactly as it was.
		BigDecimal total = Money.requireStorable(RollupCalculator.grandTotal(transactions), "Invoice total");
		List<TaxLine> taxes = RollupCalculator.taxes(transactions);
		List<FeeLine> fees = RollupCalculator.fees(transactions);
		taxes.forEach(tax -> Money.requireStorable(tax.amount(), "Tax rollup '" + tax.name() + "'"));
		fees.forEach(fee -> Money.requireStorable(fee.amount(), "Fee rollup '" + fee.name() + "'"));

		this.currency = currencies.iterator().next();
		this.grandTotal = total;
		replace(lines, transactions.stream().map(InvoiceLine::of).toList());
		replace(taxRollup, taxes);
		replace(feeRollup, fees);
	}

	private static <T> void replace(List<T> target, List<T> values) {
		target.clear();
		target.addAll(values);
	}

}
