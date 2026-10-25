package com.koushik.invoice.invoicing.domain;

import java.util.Map;
import java.util.Set;

import com.koushik.invoice.common.error.ConflictException;

public class MixedCurrencyException extends ConflictException {

	private final Set<String> currencies;

	public MixedCurrencyException(Set<String> currencies) {
		super("An invoice must have a single currency; got " + currencies);
		this.currencies = Set.copyOf(currencies);
	}

	@Override
	public Map<String, Object> properties() {
		return Map.of("currencies", currencies.stream().sorted().toList());
	}

}
