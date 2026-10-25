package com.koushik.invoice.transaction.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.koushik.invoice.transaction.domain.FeeLine;

public record FeeLineDto(@NotBlank @Size(max = 128) String name,
		@NotNull @Digits(integer = 17, fraction = 2) BigDecimal amount) {

	public static FeeLineDto from(FeeLine line) {
		return new FeeLineDto(line.name(), line.amount());
	}

	public FeeLine toDomain() {
		return new FeeLine(name, amount);
	}

}
