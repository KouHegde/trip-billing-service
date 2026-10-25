package com.koushik.invoice.invoicing.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.koushik.invoice.common.Ids;
import com.koushik.invoice.common.error.BadRequestException;
import com.koushik.invoice.common.error.ConflictException;
import com.koushik.invoice.common.error.NotFoundException;
import com.koushik.invoice.common.pagination.CursorPage;
import com.koushik.invoice.common.pagination.PageQuery;
import com.koushik.invoice.invoicing.application.dto.InvoiceRequest;
import com.koushik.invoice.invoicing.application.dto.InvoiceResponse;
import com.koushik.invoice.invoicing.domain.BillableTransaction;
import com.koushik.invoice.invoicing.domain.Invoice;
import com.koushik.invoice.invoicing.domain.InvoiceRepository;
import com.koushik.invoice.transaction.application.TransactionService;
import com.koushik.invoice.transaction.application.dto.FeeLineDto;
import com.koushik.invoice.transaction.application.dto.TaxLineDto;
import com.koushik.invoice.transaction.application.dto.TransactionResponse;
import com.koushik.invoice.transaction.application.dto.metadata.NavanFeeMetadata;

/** Invoice use cases. Each public method is one DB transaction; a failure leaves stored invoices untouched. */
@Service
public class InvoiceService {

	private final InvoiceRepository repository;

	private final TransactionService transactions;

	private final InvoiceMapper mapper;

	private final JsonMapper jsonMapper;

	InvoiceService(InvoiceRepository repository, TransactionService transactions, InvoiceMapper mapper,
			JsonMapper jsonMapper) {
		this.repository = repository;
		this.transactions = transactions;
		this.mapper = mapper;
		this.jsonMapper = jsonMapper;
	}

	/**
	 * Locks the listed transactions (so no PATCH or DELETE can change them mid-copy), copies them into a new
	 * invoice and commits. Any 404/409 rolls back the whole transaction: nothing is written.
	 */
	@Transactional
	public InvoiceResponse create(InvoiceRequest request) {
		Invoice invoice = Invoice.issue(billable(transactionIds(request)));
		return mapper.toResponse(repository.saveAndFlush(invoice));
	}

	@Transactional(readOnly = true)
	public InvoiceResponse get(String id) {
		return mapper.toResponse(Ids.parse(id)
			.flatMap(repository::findById)
			.orElseThrow(() -> new NotFoundException("Invoice", id)));
	}

	@Transactional(readOnly = true)
	public CursorPage<InvoiceResponse> list(PageQuery page) {
		Limit limit = Limit.of(page.limit() + 1);
		List<Invoice> rows = page.after() == null ? repository.findAllByOrderByIdDesc(limit)
				: repository.findByIdLessThanOrderByIdDesc(page.after(), limit);
		return page.toPage(rows, Invoice::getId, mapper::toResponse);
	}

	/** Rebuilds the snapshot from the transactions' current state, replacing every line atomically. */
	@Transactional
	public InvoiceResponse reissue(String id, InvoiceRequest request) {
		Invoice invoice = lock(id);
		invoice.reissue(billable(transactionIds(request)));
		repository.flush();
		return mapper.toResponse(invoice);
	}

	@Transactional
	public void delete(String id) {
		repository.delete(lock(id));
	}

	private Invoice lock(String id) {
		UUID uuid = Ids.parse(id).orElseThrow(() -> new NotFoundException("Invoice", id));
		repository.lockById(uuid).orElseThrow(() -> new NotFoundException("Invoice", id));
		return repository.findById(uuid).orElseThrow(() -> new NotFoundException("Invoice", id));
	}

	private List<BillableTransaction> billable(List<UUID> ids) {
		return transactions.lockForInvoicing(ids).stream().map(this::toBillable).toList();
	}

	/**
	 * The snapshot is the exact representation {@code GET /transactions/{id}} returns, serialised to JSON
	 * (Memento), so an invoice shows what the transaction looked like when it was billed.
	 */
	private BillableTransaction toBillable(TransactionResponse transaction) {
		String feeKind = transaction.metadata() instanceof NavanFeeMetadata fee ? fee.feeKind().code() : null;
		return new BillableTransaction(transaction.id(), transaction.type().code(), feeKind,
				transaction.externalId(), transaction.occurredAt(), transaction.currency(), transaction.total(),
				transaction.taxLines().stream().map(TaxLineDto::toDomain).toList(),
				transaction.feeLines().stream().map(FeeLineDto::toDomain).toList(),
				jsonMapper.writeValueAsString(transaction));
	}

	/**
	 * Accepts {@code transaction_id} or {@code transaction_ids}. A repeated id is a 409 (never silently
	 * deduplicated); an id that is not a UUID cannot exist and is reported as not found.
	 */
	private static List<UUID> transactionIds(InvoiceRequest request) {
		boolean single = request.transactionId() != null;
		boolean many = request.transactionIds() != null;
		if (single == many) {
			throw new BadRequestException("Provide exactly one of transaction_id or transaction_ids");
		}
		List<String> raw = single ? List.of(request.transactionId()) : request.transactionIds();
		if (raw.isEmpty()) {
			throw new BadRequestException("transaction_ids must not be empty");
		}
		List<UUID> ids = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (String value : raw) {
			if (value == null) {
				throw new BadRequestException("transaction_ids must not contain null");
			}
			if (!seen.add(value.toLowerCase())) {
				throw new ConflictException("Transaction " + value + " is listed more than once");
			}
			Ids.parse(value).ifPresentOrElse(ids::add, () -> missing.add(value));
		}
		if (!missing.isEmpty()) {
			throw new NotFoundException("Transaction", missing);
		}
		return ids;
	}

}
