package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import com.koushik.invoice.common.validation.Money;

/** A carrier or merchant fee on a transaction (e.g. YQ, booking fee). Kept separate from taxes. */
@Embeddable
public record FeeLine(@Column(name = "name") String name, @Column(name = "amount") BigDecimal amount) {

	public FeeLine {
		amount = Money.normalize(amount);
	}

}
