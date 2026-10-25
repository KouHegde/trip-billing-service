package com.koushik.invoice.transaction.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

/** One flight segment. {@code from}/{@code to} are SQL reserved words, hence the column names. */
@Embeddable
public record FlightCoupon(@Column(name = "carrier") @Nullable String carrier,
		@Column(name = "flight_number") String flightNumber, @Column(name = "from_airport") String from,
		@Column(name = "to_airport") String to, @Column(name = "departure_at") Instant departure,
		@Column(name = "arrival_at") Instant arrival) {
}
