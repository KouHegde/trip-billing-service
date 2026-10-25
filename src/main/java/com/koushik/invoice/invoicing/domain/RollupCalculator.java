package com.koushik.invoice.invoicing.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.koushik.invoice.common.validation.Money;
import com.koushik.invoice.transaction.domain.FeeLine;
import com.koushik.invoice.transaction.domain.TaxLine;

/**
 * Invoice-level rollups. Taxes group by (name, rate) so "VAT 7%" and "VAT 19%" stay apart; fees group by
 * name. Groups keep the order in which they first appear on the invoice.
 */
public final class RollupCalculator {

	private RollupCalculator() {
	}

	public static List<TaxLine> taxes(List<BillableTransaction> transactions) {
		Map<TaxKey, BigDecimal> sums = new LinkedHashMap<>();
		for (BillableTransaction transaction : transactions) {
			for (TaxLine line : transaction.taxLines()) {
				sums.merge(new TaxKey(line.name(), line.rate()), line.amount(), BigDecimal::add);
			}
		}
		return sums.entrySet()
			.stream()
			.map(e -> new TaxLine(e.getKey().name(), e.getKey().rate(), Money.normalize(e.getValue())))
			.toList();
	}

	public static List<FeeLine> fees(List<BillableTransaction> transactions) {
		Map<String, BigDecimal> sums = new LinkedHashMap<>();
		for (BillableTransaction transaction : transactions) {
			for (FeeLine line : transaction.feeLines()) {
				sums.merge(line.name(), line.amount(), BigDecimal::add);
			}
		}
		return sums.entrySet().stream().map(e -> new FeeLine(e.getKey(), Money.normalize(e.getValue()))).toList();
	}

	public static BigDecimal grandTotal(List<BillableTransaction> transactions) {
		return Money.normalize(
				transactions.stream().map(BillableTransaction::total).reduce(BigDecimal.ZERO, BigDecimal::add));
	}

	/** Rates are already normalised by {@link TaxLine}, so record equality groups 0.07 and 0.070 together. */
	private record TaxKey(String name, @Nullable BigDecimal rate) {
	}

}
