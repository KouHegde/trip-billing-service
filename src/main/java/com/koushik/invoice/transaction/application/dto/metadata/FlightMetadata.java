package com.koushik.invoice.transaction.application.dto.metadata;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

public record FlightMetadata(@NotBlank @Size(max = 64) String origin, @NotBlank @Size(max = 64) String destination,
		@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal fare,
		@NotEmpty List<@NotNull @Valid Coupon> coupons) implements TransactionMetadata {

	public record Coupon(@Nullable @Size(max = 8) String carrier, @NotBlank @Size(max = 16) String flightNumber,
			@NotBlank @Size(max = 64) String from, @NotBlank @Size(max = 64) String to, @NotNull Instant departure,
			@NotNull Instant arrival) {
	}

}
