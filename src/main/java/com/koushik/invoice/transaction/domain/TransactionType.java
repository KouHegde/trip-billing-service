package com.koushik.invoice.transaction.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TransactionType {

	FLIGHT(Codes.FLIGHT), HOTEL(Codes.HOTEL), RAIL(Codes.RAIL), NAVAN_FEE(Codes.NAVAN_FEE);

	private final String code;

	TransactionType(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}

	/** Discriminator values, as constants so {@code @DiscriminatorValue} can reference them. */
	public static final class Codes {

		public static final String FLIGHT = "flight";

		public static final String HOTEL = "hotel";

		public static final String RAIL = "rail";

		public static final String NAVAN_FEE = "navan_fee";

		private Codes() {
		}

	}

}
