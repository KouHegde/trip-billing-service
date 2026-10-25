package com.koushik.invoice.transaction.api;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.koushik.invoice.common.pagination.CursorPage;
import com.koushik.invoice.common.pagination.PageQuery;
import com.koushik.invoice.transaction.application.TransactionService;
import com.koushik.invoice.transaction.application.dto.CreateTransactionRequest;
import com.koushik.invoice.transaction.application.dto.PatchTransactionRequest;
import com.koushik.invoice.transaction.application.dto.TransactionResponse;

@RestController
@RequestMapping("/transactions")
class TransactionController {

	private final TransactionService transactions;

	TransactionController(TransactionService transactions) {
		this.transactions = transactions;
	}

	@PostMapping
	ResponseEntity<TransactionResponse> create(@Valid @RequestBody CreateTransactionRequest request) {
		TransactionResponse created = transactions.create(request);
		return ResponseEntity.created(URI.create("/transactions/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	TransactionResponse get(@PathVariable String id) {
		return transactions.get(id);
	}

	@GetMapping
	CursorPage<TransactionResponse> list(@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor) {
		return transactions.list(PageQuery.of(limit, cursor));
	}

	@PatchMapping("/{id}")
	TransactionResponse patch(@PathVariable String id, @Valid @RequestBody PatchTransactionRequest request) {
		return transactions.patch(id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable String id) {
		transactions.delete(id);
		return ResponseEntity.noContent().build();
	}

}
