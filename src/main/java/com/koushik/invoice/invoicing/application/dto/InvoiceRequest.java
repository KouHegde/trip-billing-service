package com.koushik.invoice.invoicing.application.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

/** Body of {@code POST /invoices} and {@code PATCH /invoices/{id}}: exactly one of the two fields. */
public record InvoiceRequest(@Nullable String transactionId, @Nullable List<String> transactionIds) {
}
