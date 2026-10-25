package com.koushik.invoice.transaction.application.handler;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.koushik.invoice.transaction.application.dto.metadata.RailMetadata;
import com.koushik.invoice.transaction.domain.RailLeg;
import com.koushik.invoice.transaction.domain.RailTransaction;
import com.koushik.invoice.transaction.domain.TransactionHeader;
import com.koushik.invoice.transaction.domain.TransactionType;

@Component
class RailHandler extends AbstractTransactionHandler<RailTransaction, RailMetadata> {

	RailHandler() {
		super(TransactionType.RAIL, RailTransaction.class, RailMetadata.class);
	}

	@Override
	protected List<String> validate(RailMetadata metadata) {
		List<String> errors = new ArrayList<>();
		for (int i = 0; i < metadata.legs().size(); i++) {
			RailMetadata.Leg leg = metadata.legs().get(i);
			if (!leg.arrival().isAfter(leg.departure())) {
				errors.add("metadata.legs[" + i + "]: arrival must be after departure");
			}
		}
		return errors;
	}

	@Override
	protected RailTransaction newEntity(TransactionHeader header, RailMetadata metadata) {
		return new RailTransaction(header, legs(metadata));
	}

	@Override
	protected void replace(RailTransaction transaction, RailMetadata metadata) {
		transaction.replaceLegs(legs(metadata));
	}

	@Override
	protected RailMetadata toDto(RailTransaction transaction) {
		return new RailMetadata(transaction.getLegs()
			.stream()
			.map(l -> new RailMetadata.Leg(l.origin(), l.destination(), l.departure(), l.arrival(), l.trainNumber(),
					l.travelClass()))
			.toList());
	}

	private static List<RailLeg> legs(RailMetadata metadata) {
		return metadata.legs()
			.stream()
			.map(l -> new RailLeg(l.origin(), l.destination(), l.departure(), l.arrival(), l.trainNumber(),
					l.travelClass()))
			.toList();
	}

}
