package com.koushik.invoice.transaction.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "rail_transactions")
@DiscriminatorValue(TransactionType.Codes.RAIL)
@PrimaryKeyJoinColumn(name = "transaction_id")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RailTransaction extends Transaction {

	@ElementCollection
	@CollectionTable(name = "rail_legs", joinColumns = @JoinColumn(name = "transaction_id"))
	@OrderColumn(name = "leg_no")
	private List<RailLeg> legs = new ArrayList<>();

	public RailTransaction(TransactionHeader header, List<RailLeg> legs) {
		super(header);
		replaceLegs(legs);
	}

	@Override
	public TransactionType getType() {
		return TransactionType.RAIL;
	}

	public List<RailLeg> getLegs() {
		return List.copyOf(legs);
	}

	public void replaceLegs(List<RailLeg> legs) {
		this.legs.clear();
		this.legs.addAll(legs);
	}

}
