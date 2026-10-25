package com.koushik.invoice.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** A Navan-owned charge (trip fee, agent call fee), billed as its own transaction. */
@Entity
@Table(name = "navan_fee_transactions")
@DiscriminatorValue(TransactionType.Codes.NAVAN_FEE)
@PrimaryKeyJoinColumn(name = "transaction_id")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NavanFeeTransaction extends Transaction {

	@Column(name = "fee_kind", nullable = false)
	private NavanFeeKind feeKind;

	private @Nullable String description;

	/** The booking this fee belongs to; a plain value because that booking may not be stored here. */
	@Column(name = "related_external_id")
	private @Nullable String relatedExternalId;

	public NavanFeeTransaction(TransactionHeader header, NavanFeeKind feeKind, @Nullable String description,
			@Nullable String relatedExternalId) {
		super(header);
		replaceDetails(feeKind, description, relatedExternalId);
	}

	@Override
	public TransactionType getType() {
		return TransactionType.NAVAN_FEE;
	}

	public void replaceDetails(NavanFeeKind feeKind, @Nullable String description,
			@Nullable String relatedExternalId) {
		this.feeKind = feeKind;
		this.description = description;
		this.relatedExternalId = relatedExternalId;
	}

}
