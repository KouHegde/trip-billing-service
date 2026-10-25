package com.koushik.invoice.transaction.application.dto.metadata;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.koushik.invoice.transaction.domain.NavanFeeKind;

public record NavanFeeMetadata(@NotNull NavanFeeKind feeKind, @Nullable @Size(max = 512) String description,
		@Nullable @Size(max = 128) String relatedExternalId) implements TransactionMetadata {
}
