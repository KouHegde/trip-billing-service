package com.koushik.invoice.transaction.application.handler;

import java.util.List;

import com.koushik.invoice.transaction.application.dto.metadata.TransactionMetadata;
import com.koushik.invoice.transaction.domain.Transaction;
import com.koushik.invoice.transaction.domain.TransactionHeader;
import com.koushik.invoice.transaction.domain.TransactionType;

/**
 * Template method: does the casts once so each concrete handler works with its own entity and metadata
 * types.
 */
abstract class AbstractTransactionHandler<T extends Transaction, M extends TransactionMetadata>
		implements TransactionTypeHandler {

	private final TransactionType type;

	private final Class<T> entityType;

	private final Class<M> metadataType;

	protected AbstractTransactionHandler(TransactionType type, Class<T> entityType, Class<M> metadataType) {
		this.type = type;
		this.entityType = entityType;
		this.metadataType = metadataType;
	}

	@Override
	public final TransactionType type() {
		return type;
	}

	@Override
	public final Class<M> metadataType() {
		return metadataType;
	}

	@Override
	public final List<String> crossFieldErrors(TransactionMetadata metadata) {
		return validate(metadataType.cast(metadata));
	}

	@Override
	public final Transaction create(TransactionHeader header, TransactionMetadata metadata) {
		return newEntity(header, metadataType.cast(metadata));
	}

	@Override
	public final void replaceMetadata(Transaction transaction, TransactionMetadata metadata) {
		replace(entityType.cast(transaction), metadataType.cast(metadata));
	}

	@Override
	public final TransactionMetadata toMetadata(Transaction transaction) {
		return toDto(entityType.cast(transaction));
	}

	protected List<String> validate(M metadata) {
		return List.of();
	}

	protected abstract T newEntity(TransactionHeader header, M metadata);

	protected abstract void replace(T transaction, M metadata);

	protected abstract M toDto(T transaction);

}
