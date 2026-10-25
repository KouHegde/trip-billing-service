package com.koushik.invoice.transaction.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import com.koushik.invoice.transaction.domain.TransactionType;

/**
 * Body of {@code PATCH /transactions/{id}}: absent fields stay unchanged; lists and {@code metadata} are
 * replaced whole. {@code type}, {@code external_id} and {@code currency} are identity and may only repeat the
 * stored value.
 */
public record PatchTransactionRequest(@Nullable TransactionType type, @Nullable String externalId,
		@Nullable String currency, @Nullable Instant occurredAt,
		@Nullable @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal total,
		@Nullable List<@NotNull @Valid TaxLineDto> taxLines, @Nullable List<@NotNull @Valid FeeLineDto> feeLines,
		@Nullable JsonNode metadata) {
}
