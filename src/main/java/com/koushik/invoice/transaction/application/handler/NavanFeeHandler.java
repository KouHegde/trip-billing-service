package com.koushik.invoice.transaction.application.handler;

import org.springframework.stereotype.Component;

import com.koushik.invoice.transaction.application.dto.metadata.NavanFeeMetadata;
import com.koushik.invoice.transaction.domain.NavanFeeTransaction;
import com.koushik.invoice.transaction.domain.TransactionHeader;
import com.koushik.invoice.transaction.domain.TransactionType;

@Component
class NavanFeeHandler extends AbstractTransactionHandler<NavanFeeTransaction, NavanFeeMetadata> {

	NavanFeeHandler() {
		super(TransactionType.NAVAN_FEE, NavanFeeTransaction.class, NavanFeeMetadata.class);
	}

	@Override
	protected NavanFeeTransaction newEntity(TransactionHeader header, NavanFeeMetadata metadata) {
		return new NavanFeeTransaction(header, metadata.feeKind(), metadata.description(),
				metadata.relatedExternalId());
	}

	@Override
	protected void replace(NavanFeeTransaction transaction, NavanFeeMetadata metadata) {
		transaction.replaceDetails(metadata.feeKind(), metadata.description(), metadata.relatedExternalId());
	}

	@Override
	protected NavanFeeMetadata toDto(NavanFeeTransaction transaction) {
		return new NavanFeeMetadata(transaction.getFeeKind(), transaction.getDescription(),
				transaction.getRelatedExternalId());
	}

}
