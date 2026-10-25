package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.koushik.invoice.common.validation.Money;

@Entity
@Table(name = "flight_transactions")
@DiscriminatorValue(TransactionType.Codes.FLIGHT)
@PrimaryKeyJoinColumn(name = "transaction_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightTransaction extends Transaction {

	@Column(nullable = false)
	private String origin;

	@Column(nullable = false)
	private String destination;

	@Column(nullable = false)
	private BigDecimal fare;

	@ElementCollection
	@CollectionTable(name = "flight_coupons", joinColumns = @JoinColumn(name = "transaction_id"))
	@OrderColumn(name = "coupon_no")
	@Getter(AccessLevel.NONE)
	private List<FlightCoupon> coupons = new ArrayList<>();

	public FlightTransaction(TransactionHeader header, String origin, String destination, BigDecimal fare,
			List<FlightCoupon> coupons) {
		super(header);
		replaceDetails(origin, destination, fare, coupons);
	}

	@Override
	public TransactionType getType() {
		return TransactionType.FLIGHT;
	}

	public List<FlightCoupon> getCoupons() {
		return List.copyOf(coupons);
	}

	public void replaceDetails(String origin, String destination, BigDecimal fare, List<FlightCoupon> coupons) {
		this.origin = origin;
		this.destination = destination;
		this.fare = Money.normalize(fare);
		this.coupons.clear();
		this.coupons.addAll(coupons);
	}

}
