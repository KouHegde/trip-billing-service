package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

import com.koushik.invoice.common.validation.Money;

/** A government tax on a transaction. Value object: no identity, replaced as a whole list. */
@Embeddable
public record TaxLine(@Column(name = "name") String name, @Column(name = "rate") @Nullable BigDecimal rate,
		@Column(name = "amount") BigDecimal amount) {

	public TaxLine {
		rate = Rates.normalize(rate);
		amount = Money.normalize(amount);
	}

}
