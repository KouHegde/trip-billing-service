package com.koushik.invoice.common.validation;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.jspecify.annotations.Nullable;

import com.koushik.invoice.common.error.BadRequestException;

/** Money is stored as NUMERIC(19,2). Inputs are validated to at most 2 decimals, so normalising never rounds. */
public final class Money {

	public static final int SCALE = 2;

	/** Largest value NUMERIC(19,2) can hold: 17 integer digits and 2 decimals. */
	public static final BigDecimal MAX = new BigDecimal("99999999999999999.99");

	private Money() {
	}

	/**
	 * Each input amount is validated on its own, but invoices add them up; a sum can exceed the column. Checked
	 * before writing so the client gets a clear 400 instead of a database overflow error.
	 */
	public static BigDecimal requireStorable(BigDecimal amount, String what) {
		if (amount.abs().compareTo(MAX) > 0) {
			throw new BadRequestException(what + " " + amount.toPlainString()
					+ " exceeds the largest storable amount " + MAX.toPlainString());
		}
		return amount;
	}

	public static BigDecimal normalize(BigDecimal amount) {
		return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
	}

	public static @Nullable BigDecimal normalizeNullable(@Nullable BigDecimal amount) {
		return amount == null ? null : normalize(amount);
	}

}
