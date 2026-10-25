package com.koushik.invoice.transaction.domain;

import com.fasterxml.jackson.annotation.JsonValue;

public enum NavanFeeKind {

	TRIP_FEE("trip_fee"), AGENT_CALL_FEE("agent_call_fee");

	private final String code;

	NavanFeeKind(String code) {
		this.code = code;
	}

	@JsonValue
	public String code() {
		return code;
	}

	public static NavanFeeKind fromCode(String code) {
		for (NavanFeeKind kind : values()) {
			if (kind.code.equals(code)) {
				return kind;
			}
		}
		throw new IllegalArgumentException("Unknown fee_kind: " + code);
	}

}
