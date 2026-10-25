package com.koushik.invoice.transaction.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.koushik.invoice.transaction.application.dto.FeeLineDto;
import com.koushik.invoice.transaction.application.dto.TaxLineDto;
import com.koushik.invoice.transaction.application.dto.TransactionResponse;
import com.koushik.invoice.transaction.application.handler.TransactionHandlerRegistry;
import com.koushik.invoice.transaction.domain.FeeLine;
import com.koushik.invoice.transaction.domain.TaxLine;
import com.koushik.invoice.transaction.domain.Transaction;

@Component
class TransactionMapper {

	private final TransactionHandlerRegistry handlers;

	TransactionMapper(TransactionHandlerRegistry handlers) {
		this.handlers = handlers;
	}

	TransactionResponse toResponse(Transaction transaction) {
		return new TransactionResponse(transaction.getId(), transaction.getType(), transaction.getExternalId(),
				transaction.getCurrency(), transaction.getOccurredAt(), transaction.getTotal(),
				transaction.getTaxLines().stream().map(TaxLineDto::from).toList(),
				transaction.getFeeLines().stream().map(FeeLineDto::from).toList(),
				handlers.handlerFor(transaction.getType()).toMetadata(transaction), transaction.getCreatedAt(),
				transaction.getUpdatedAt());
	}

	static List<TaxLine> taxLines(List<TaxLineDto> dtos) {
		return dtos.stream().map(TaxLineDto::toDomain).toList();
	}

	static List<FeeLine> feeLines(List<FeeLineDto> dtos) {
		return dtos.stream().map(FeeLineDto::toDomain).toList();
	}

}
