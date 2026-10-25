package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Everything type-specific about a hotel transaction, grouped so it can be replaced in one call. */
public record HotelStay(String name, Address address, LocalDate checkIn, LocalDate checkOut, int nights,
		BigDecimal roomRatePerNight, List<HotelExtra> extras) {
}
