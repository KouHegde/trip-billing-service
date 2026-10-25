package com.koushik.invoice.invoicing.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.koushik.invoice.common.validation.Money;
import com.koushik.invoice.invoicing.application.dto.InvoiceLineResponse;
import com.koushik.invoice.invoicing.application.dto.InvoiceResponse;
import com.koushik.invoice.invoicing.application.dto.InvoiceTotals;
import com.koushik.invoice.invoicing.domain.Invoice;
import com.koushik.invoice.invoicing.domain.InvoiceLine;
import com.koushik.invoice.transaction.application.dto.FeeLineDto;
import com.koushik.invoice.transaction.application.dto.TaxLineDto;

@Component
class InvoiceMapper {

	private final JsonMapper jsonMapper;

	InvoiceMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	InvoiceResponse toResponse(Invoice invoice) {
		return new InvoiceResponse(invoice.getId(), invoice.getCurrency(),
				invoice.getLines().stream().map(this::toLine).toList(),
				invoice.getTaxRollup().stream().map(TaxLineDto::from).toList(),
				invoice.getFeeRollup().stream().map(FeeLineDto::from).toList(), totals(invoice),
				invoice.getCreatedAt(), invoice.getUpdatedAt());
	}

	private InvoiceLineResponse toLine(InvoiceLine line) {
		JsonNode snapshot = jsonMapper.readTree(line.snapshot());
		return new InvoiceLineResponse(line.transactionId(), line.transactionType(), line.feeKind(),
				line.externalId(), line.occurredAt(), line.total(), snapshot.get("tax_lines"),
				snapshot.get("fee_lines"), snapshot.get("metadata"));
	}

	/** Derived from the frozen lines, so they can never drift from what was billed. */
	private static InvoiceTotals totals(Invoice invoice) {
		Map<String, BigDecimal> byType = new LinkedHashMap<>();
		Map<String, BigDecimal> byFeeKind = new LinkedHashMap<>();
		for (InvoiceLine line : invoice.getLines()) {
			byType.merge(line.transactionType(), line.total(), BigDecimal::add);
			if (line.feeKind() != null) {
				byFeeKind.merge(line.feeKind(), line.total(), BigDecimal::add);
			}
		}
		byType.replaceAll((type, amount) -> Money.normalize(amount));
		byFeeKind.replaceAll((kind, amount) -> Money.normalize(amount));
		return new InvoiceTotals(byType, byFeeKind, invoice.getGrandTotal());
	}

}
