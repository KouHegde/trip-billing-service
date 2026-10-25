package com.koushik.invoice.common.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** An ISO 4217 currency code known to the JDK, e.g. {@code EUR}. Null is left to {@code @NotNull}. */
@Documented
@Constraint(validatedBy = CurrencyValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface Currency {

	String message() default "must be an ISO 4217 currency code";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
