package com.koushik.invoice.transaction.application.handler;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.koushik.invoice.transaction.application.dto.metadata.FlightMetadata;
import com.koushik.invoice.transaction.domain.FlightCoupon;
import com.koushik.invoice.transaction.domain.FlightTransaction;
import com.koushik.invoice.transaction.domain.TransactionHeader;
import com.koushik.invoice.transaction.domain.TransactionType;

@Component
class FlightHandler extends AbstractTransactionHandler<FlightTransaction, FlightMetadata> {

	FlightHandler() {
		super(TransactionType.FLIGHT, FlightTransaction.class, FlightMetadata.class);
	}

	@Override
	protected List<String> validate(FlightMetadata metadata) {
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < metadata.coupons().size(); i++) {
			FlightMetadata.Coupon coupon = metadata.coupons().get(i);
			if (!coupon.arrival().isAfter(coupon.departure())) {
				errors.add("metadata.coupons[" + i + "]: arrival must be after departure");
			}
		}
		return errors;
	}

	@Override
	protected FlightTransaction newEntity(TransactionHeader header, FlightMetadata metadata) {
		return new FlightTransaction(header, metadata.origin(), metadata.destination(), metadata.fare(),
				coupons(metadata));
	}

	@Override
	protected void replace(FlightTransaction transaction, FlightMetadata metadata) {
		transaction.replaceDetails(metadata.origin(), metadata.destination(), metadata.fare(), coupons(metadata));
	}

	@Override
	protected FlightMetadata toDto(FlightTransaction transaction) {
		return new FlightMetadata(transaction.getOrigin(), transaction.getDestination(), transaction.getFare(),
				transaction.getCoupons()
					.stream()
					.map(c -> new FlightMetadata.Coupon(c.carrier(), c.flightNumber(), c.from(), c.to(),
							c.departure(), c.arrival()))
					.toList());
	}

	private static List<FlightCoupon> coupons(FlightMetadata metadata) {
		return metadata.coupons()
			.stream()
			.map(c -> new FlightCoupon(c.carrier(), c.flightNumber(), c.from(), c.to(), c.departure(), c.arrival()))
			.toList();
	}

}
