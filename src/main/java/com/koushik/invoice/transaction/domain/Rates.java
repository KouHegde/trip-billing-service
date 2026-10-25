package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

/** Tax rates are stored as NUMERIC(9,6); trailing zeros are dropped so 0.070000 reads back as 0.07. */
public final class Rates {

	private Rates() {
	}

	public static @Nullable BigDecimal normalize(@Nullable BigDecimal rate) {
		if (rate == null) {
			return null;
		}
		BigDecimal stripped = rate.stripTrailingZeros();
		return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
	}

}
