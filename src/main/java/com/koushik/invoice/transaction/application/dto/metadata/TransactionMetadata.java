package com.koushik.invoice.transaction.application.dto.metadata;

/** Type-specific part of a transaction; one record per {@code type}. */
public sealed interface TransactionMetadata permits FlightMetadata, HotelMetadata, RailMetadata, NavanFeeMetadata {
}
