package com.koushik.invoice.transaction.application.dto.metadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

public record HotelMetadata(@NotBlank @Size(max = 256) String name, @NotNull @Valid Address address,
		@NotNull LocalDate checkIn, @NotNull LocalDate checkOut, @NotNull @Positive Integer nights,
		@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal roomRatePerNight,
		@Nullable List<@NotNull @Valid Extra> extras) implements TransactionMetadata {

	public record Address(@NotBlank @Size(max = 256) String street, @NotBlank @Size(max = 128) String city,
			@Nullable @Size(max = 32) String postalCode,
			@NotNull @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166 alpha-2 code") String country) {
	}

	public record Extra(@NotBlank @Size(max = 128) String name,
			@NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount) {
	}

}
