package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.koushik.invoice.common.validation.Money;

/** Informational extra on a stay (e.g. breakfast). Billing uses fee_lines; extras never add to totals. */
@Embeddable
public record HotelExtra(@Column(name = "name") String name, @Column(name = "amount") BigDecimal amount) {

	public HotelExtra {
		amount = Money.normalize(amount);
	}

}
