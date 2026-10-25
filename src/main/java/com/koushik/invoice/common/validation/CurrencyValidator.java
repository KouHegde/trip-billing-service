package com.koushik.invoice.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CurrencyValidator implements ConstraintValidator<Currency, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		if (!value.matches("[A-Z]{3}")) {
			return false;
		}
		try {
			java.util.Currency.getInstance(value);
			return true;
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
	}

}
