package com.koushik.invoice.transaction.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

import com.koushik.invoice.common.validation.Currency;
import com.koushik.invoice.transaction.domain.TransactionType;

/**
 * Body of {@code POST /transactions}. {@code metadata} stays untyped here: it is parsed by the type's handler
 * only after the duplicate check, so a known {@code external_id} is reported as a duplicate whatever its body.
 */
public record CreateTransactionRequest(@NotNull TransactionType type, @NotBlank @Size(max = 128) String externalId,
		@NotNull @Currency String currency, @NotNull Instant occurredAt,
		// Scale 2, never rounded: 94.005 is a 400. Negative totals (refunds) are out of scope.
		@NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal total,
		// Required but may be empty: "no tax" is an empty list, not a missing field.
		@NotNull List<@NotNull @Valid TaxLineDto> taxLines, @NotNull List<@NotNull @Valid FeeLineDto> feeLines,
		@NotNull JsonNode metadata) {
}
