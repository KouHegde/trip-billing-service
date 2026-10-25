package com.koushik.invoice.invoicing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.koushik.invoice.transaction.domain.FeeLine;
import com.koushik.invoice.transaction.domain.TaxLine;

import static org.assertj.core.api.Assertions.assertThat;

class RollupCalculatorTest {

	@Test
	void groupsTaxesByNameAndRateInFirstSeenOrder() {
		List<BillableTransaction> transactions = List.of(
				billable("100.00", List.of(tax("VAT", "0.19", "19.00"), tax("Airport tax", null, "5.00")), List.of()),
				billable("50.00", List.of(tax("VAT", "0.070", "3.50"), tax("VAT", "0.19", "1.00")), List.of()));

		assertThat(RollupCalculator.taxes(transactions)).containsExactly(tax("VAT", "0.19", "20.00"),
				tax("Airport tax", null, "5.00"), tax("VAT", "0.07", "3.50"));
	}

	@Test
	void groupsFeesByNameAndSumsGrandTotalExactly() {
		List<BillableTransaction> transactions = List.of(
				billable("0.10", List.of(), List.of(new FeeLine("YQ", new BigDecimal("0.10")))),
				billable("0.20", List.of(), List.of(new FeeLine("YQ", new BigDecimal("0.20")))));

		assertThat(RollupCalculator.fees(transactions)).containsExactly(new FeeLine("YQ", new BigDecimal("0.30")));
		assertThat(RollupCalculator.grandTotal(transactions)).isEqualTo(new BigDecimal("0.30"));
	}

	private static TaxLine tax(String name, String rate, String amount) {
		return new TaxLine(name, rate == null ? null : new BigDecimal(rate), new BigDecimal(amount));
	}

	private static BillableTransaction billable(String total, List<TaxLine> taxes, List<FeeLine> fees) {
		return new BillableTransaction(UUID.randomUUID(), "flight", null, "ext", Instant.EPOCH, "EUR",
				new BigDecimal(total), taxes, fees, "{}");
	}

}
