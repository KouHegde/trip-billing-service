package com.koushik.invoice.transaction.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores {@link NavanFeeKind} by its API code ({@code trip_fee}) to match the DB CHECK constraint. */
@Converter(autoApply = true)
class NavanFeeKindConverter implements AttributeConverter<NavanFeeKind, String> {

	@Override
	public String convertToDatabaseColumn(NavanFeeKind kind) {
		return kind == null ? null : kind.code();
	}

	@Override
	public NavanFeeKind convertToEntityAttribute(String code) {
		return code == null ? null : NavanFeeKind.fromCode(code);
	}

}
