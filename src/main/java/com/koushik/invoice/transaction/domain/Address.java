package com.koushik.invoice.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.jspecify.annotations.Nullable;

/** Hotel property address, flattened into {@code hotel_transactions}. */
@Embeddable
public record Address(@Column(name = "address_street") String street, @Column(name = "address_city") String city,
		@Column(name = "address_postal_code") @Nullable String postalCode,
		@Column(name = "address_country") String country) {
}
