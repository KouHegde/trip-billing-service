package com.koushik.invoice.invoicing.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** One snapshot line. Lists and metadata come from the frozen JSON, never from the live transaction. */
public record InvoiceLineResponse(UUID transactionId, String type, @Nullable String feeKind, String externalId,
		Instant occurredAt, BigDecimal total, JsonNode taxLines, JsonNode feeLines, JsonNode metadata) {
}
