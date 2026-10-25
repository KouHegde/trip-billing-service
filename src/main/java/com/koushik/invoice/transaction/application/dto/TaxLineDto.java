package com.koushik.invoice.transaction.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.koushik.invoice.transaction.domain.TaxLine;

public record TaxLineDto(@NotBlank @Size(max = 128) String name,
		@Nullable @PositiveOrZero @Digits(integer = 3, fraction = 6) BigDecimal rate,
		@NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount) {

	public static TaxLineDto from(TaxLine line) {
		return new TaxLineDto(line.name(), line.rate(), line.amount());
	}

	public TaxLine toDomain() {
		return new TaxLine(name, rate, amount);
	}

}
