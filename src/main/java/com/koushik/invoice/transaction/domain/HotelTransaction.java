package com.koushik.invoice.transaction.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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
@Table(name = "hotel_transactions")
@DiscriminatorValue(TransactionType.Codes.HOTEL)
@PrimaryKeyJoinColumn(name = "transaction_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotelTransaction extends Transaction {

	@Column(nullable = false)
	private String name;

	@Embedded
	private Address address;

	@Column(name = "check_in", nullable = false)
	private LocalDate checkIn;

	@Column(name = "check_out", nullable = false)
	private LocalDate checkOut;

	@Column(nullable = false)
	private int nights;

	@Column(name = "room_rate_per_night", nullable = false)
	private BigDecimal roomRatePerNight;

	@ElementCollection
	@CollectionTable(name = "hotel_extras", joinColumns = @JoinColumn(name = "transaction_id"))
	@OrderColumn(name = "line_no")
	@Getter(AccessLevel.NONE)
	private List<HotelExtra> extras = new ArrayList<>();

	public HotelTransaction(TransactionHeader header, HotelStay stay) {
		super(header);
		replaceStay(stay);
	}

	@Override
	public TransactionType getType() {
		return TransactionType.HOTEL;
	}

	public List<HotelExtra> getExtras() {
		return List.copyOf(extras);
	}

	public void replaceStay(HotelStay stay) {
		this.name = stay.name();
		this.address = stay.address();
		this.checkIn = stay.checkIn();
		this.checkOut = stay.checkOut();
		this.nights = stay.nights();
		this.roomRatePerNight = Money.normalize(stay.roomRatePerNight());
		this.extras.clear();
		this.extras.addAll(stay.extras());
	}

}
