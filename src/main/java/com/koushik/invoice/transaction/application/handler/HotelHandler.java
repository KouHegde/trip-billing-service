package com.koushik.invoice.transaction.application.handler;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.koushik.invoice.transaction.application.dto.metadata.HotelMetadata;
import com.koushik.invoice.transaction.domain.Address;
import com.koushik.invoice.transaction.domain.HotelExtra;
import com.koushik.invoice.transaction.domain.HotelStay;
import com.koushik.invoice.transaction.domain.HotelTransaction;
import com.koushik.invoice.transaction.domain.TransactionHeader;
import com.koushik.invoice.transaction.domain.TransactionType;

@Component
class HotelHandler extends AbstractTransactionHandler<HotelTransaction, HotelMetadata> {

	HotelHandler() {
		super(TransactionType.HOTEL, HotelTransaction.class, HotelMetadata.class);
	}

	/** Rejects inconsistent stays at the door: nights must match the dates (2026-03-11 to 13 is 2 nights). */
	@Override
	protected List<String> validate(HotelMetadata metadata) {
		List<String> errors = new ArrayList<>();
		if (!metadata.checkOut().isAfter(metadata.checkIn())) {
			errors.add("metadata.check_out: must be after check_in");
		}
		else if (ChronoUnit.DAYS.between(metadata.checkIn(), metadata.checkOut()) != metadata.nights()) {
			errors.add("metadata.nights: must equal the days between check_in and check_out");
		}
		return errors;
	}

	@Override
	protected HotelTransaction newEntity(TransactionHeader header, HotelMetadata metadata) {
		return new HotelTransaction(header, stay(metadata));
	}

	@Override
	protected void replace(HotelTransaction transaction, HotelMetadata metadata) {
		transaction.replaceStay(stay(metadata));
	}

	@Override
	protected HotelMetadata toDto(HotelTransaction transaction) {
		Address address = transaction.getAddress();
		return new HotelMetadata(transaction.getName(),
				new HotelMetadata.Address(address.street(), address.city(), address.postalCode(), address.country()),
				transaction.getCheckIn(), transaction.getCheckOut(), transaction.getNights(),
				transaction.getRoomRatePerNight(),
				transaction.getExtras().stream().map(e -> new HotelMetadata.Extra(e.name(), e.amount())).toList());
	}

	private static HotelStay stay(HotelMetadata metadata) {
		HotelMetadata.Address address = metadata.address();
		List<HotelExtra> extras = metadata.extras() == null ? List.of()
				: metadata.extras().stream().map(e -> new HotelExtra(e.name(), e.amount())).toList();
		return new HotelStay(metadata.name(),
				new Address(address.street(), address.city(), address.postalCode(), address.country()),
				metadata.checkIn(), metadata.checkOut(), metadata.nights(), metadata.roomRatePerNight(), extras);
	}

}
