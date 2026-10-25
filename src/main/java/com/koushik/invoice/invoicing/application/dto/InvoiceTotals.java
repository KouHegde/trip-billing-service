package com.koushik.invoice.invoicing.application.dto;

import java.math.BigDecimal;
import java.util.Map;

public record InvoiceTotals(Map<String, BigDecimal> byType, Map<String, BigDecimal> byFeeKind,
		BigDecimal grandTotal) {
}
