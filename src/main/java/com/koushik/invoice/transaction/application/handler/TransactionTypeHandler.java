package com.koushik.invoice.transaction.application.handler;

import java.util.List;

import com.koushik.invoice.transaction.application.dto.metadata.TransactionMetadata;
import com.koushik.invoice.transaction.domain.Transaction;
import com.koushik.invoice.transaction.domain.TransactionHeader;
import com.koushik.invoice.transaction.domain.TransactionType;

/** Strategy: everything that differs per transaction type. One implementation per {@link TransactionType}. */
public interface TransactionTypeHandler {

	TransactionType type();

	Class<? extends TransactionMetadata> metadataType();

	/** Rules that span several fields (beyond Bean Validation). Empty when valid. */
	List<String> crossFieldErrors(TransactionMetadata metadata);

	Transaction create(TransactionHeader header, TransactionMetadata metadata);

	void replaceMetadata(Transaction transaction, TransactionMetadata metadata);

	TransactionMetadata toMetadata(Transaction transaction);

}
