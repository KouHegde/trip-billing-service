package com.koushik.invoice.transaction.application.handler;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.koushik.invoice.transaction.domain.TransactionType;

/** Resolves the handler for a type. Fails at startup, not at request time, if a type has no handler. */
@Component
public class TransactionHandlerRegistry {

	private final Map<TransactionType, TransactionTypeHandler> handlers = new EnumMap<>(TransactionType.class);

	public TransactionHandlerRegistry(List<TransactionTypeHandler> handlers) {
		for (TransactionTypeHandler handler : handlers) {
			if (this.handlers.put(handler.type(), handler) != null) {
				throw new IllegalStateException("Two handlers registered for " + handler.type());
			}
		}
		Set<TransactionType> missing = EnumSet.allOf(TransactionType.class);
		missing.removeAll(this.handlers.keySet());
		if (!missing.isEmpty()) {
			throw new IllegalStateException("No TransactionTypeHandler for " + missing);
		}
	}

	public TransactionTypeHandler handlerFor(TransactionType type) {
		return handlers.get(type);
	}

}
