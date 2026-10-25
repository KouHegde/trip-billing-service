package com.koushik.invoice.transaction.application.dto.metadata;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record RailMetadata(@NotEmpty List<@NotNull @Valid Leg> legs) implements TransactionMetadata {

	public record Leg(@NotBlank @Size(max = 128) String origin, @NotBlank @Size(max = 128) String destination,
			@NotNull Instant departure, @NotNull Instant arrival, @Nullable @Size(max = 32) String trainNumber,
			@JsonProperty("class") @Nullable @Size(max = 32) String travelClass) {
	}

}
