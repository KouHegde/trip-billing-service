package com.koushik.invoice.invoicing.api;

import java.net.URI;

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
import com.koushik.invoice.invoicing.application.InvoiceService;
import com.koushik.invoice.invoicing.application.dto.InvoiceRequest;
import com.koushik.invoice.invoicing.application.dto.InvoiceResponse;

@RestController
@RequestMapping("/invoices")
class InvoiceController {

	private final InvoiceService invoices;

	InvoiceController(InvoiceService invoices) {
		this.invoices = invoices;
	}

	@PostMapping
	ResponseEntity<InvoiceResponse> create(@RequestBody InvoiceRequest request) {
		InvoiceResponse created = invoices.create(request);
		return ResponseEntity.created(URI.create("/invoices/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	InvoiceResponse get(@PathVariable String id) {
		return invoices.get(id);
	}

	@GetMapping
	CursorPage<InvoiceResponse> list(@RequestParam(required = false) Integer limit,
			@RequestParam(required = false) String cursor) {
		return invoices.list(PageQuery.of(limit, cursor));
	}

	@PatchMapping("/{id}")
	InvoiceResponse reissue(@PathVariable String id, @RequestBody InvoiceRequest request) {
		return invoices.reissue(id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@PathVariable String id) {
		invoices.delete(id);
		return ResponseEntity.noContent().build();
	}

}
