package com.koushik.invoice.transaction.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

/** One train ride; a journey with a change of train is two legs. */
@Embeddable
public record RailLeg(@Column(name = "origin") String origin, @Column(name = "destination") String destination,
		@Column(name = "departure_at") Instant departure, @Column(name = "arrival_at") Instant arrival,
		@Column(name = "train_number") @Nullable String trainNumber,
		@Column(name = "travel_class") @Nullable String travelClass) {
}
