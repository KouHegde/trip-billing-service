package com.koushik.invoice.transaction.application;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.koushik.invoice.common.Ids;
import com.koushik.invoice.common.error.BadRequestException;
import com.koushik.invoice.common.error.NotFoundException;
import com.koushik.invoice.common.pagination.CursorPage;
import com.koushik.invoice.common.pagination.PageQuery;
import com.koushik.invoice.transaction.application.dto.CreateTransactionRequest;
import com.koushik.invoice.transaction.application.dto.PatchTransactionRequest;
import com.koushik.invoice.transaction.application.dto.TransactionResponse;
import com.koushik.invoice.transaction.application.dto.metadata.TransactionMetadata;
import com.koushik.invoice.transaction.application.handler.TransactionHandlerRegistry;
import com.koushik.invoice.transaction.domain.InvoiceUsageChecker;
import com.koushik.invoice.transaction.domain.Transaction;
import com.koushik.invoice.transaction.domain.TransactionHeader;
import com.koushik.invoice.transaction.domain.TransactionRepository;

/** Transaction use cases. Each public method is one DB transaction. */
@Service
public class TransactionService {

	private static final String EXTERNAL_ID_CONSTRAINT = "uk_transactions_external_id";

	private static final String UNIQUE_VIOLATION = "23505";

	private final TransactionRepository repository;

	private final TransactionHandlerRegistry handlers;

	private final MetadataParser metadataParser;

	private final TransactionMapper mapper;

	private final InvoiceUsageChecker invoiceUsage;

	private final TransactionTemplate transactionTemplate;

	TransactionService(TransactionRepository repository, TransactionHandlerRegistry handlers,
			MetadataParser metadataParser, TransactionMapper mapper, InvoiceUsageChecker invoiceUsage,
			TransactionTemplate transactionTemplate) {
		this.repository = repository;
		this.handlers = handlers;
		this.metadataParser = metadataParser;
		this.mapper = mapper;
		this.invoiceUsage = invoiceUsage;
		this.transactionTemplate = transactionTemplate;
	}

	/**
	 * Identity before content: a known {@code external_id} is a duplicate whatever the body says. The lookup is
	 * only a fast path; the unique constraint decides when two creates race, and the loser is reported with the
	 * winner's id.
	 */
	public TransactionResponse create(CreateTransactionRequest request) {
		repository.findIdByExternalId(request.externalId()).ifPresent(existingId -> {
			throw new DuplicateTransactionException(request.externalId(), existingId);
		});
		TransactionMetadata metadata = metadataParser.parse(request.type(), request.metadata());
		TransactionHeader header = new TransactionHeader(request.externalId(), request.currency(),
				request.occurredAt(), request.total(), TransactionMapper.taxLines(request.taxLines()),
				TransactionMapper.feeLines(request.feeLines()));
		// The insert runs in its own DB transaction so a unique violation can be caught here and the winner's id
		// read in a fresh one; inside a single @Transactional method the failed transaction would be unusable.
		try {
			return transactionTemplate.execute(status -> {
				Transaction transaction = handlers.handlerFor(request.type()).create(header, metadata);
				return mapper.toResponse(repository.saveAndFlush(transaction));
			});
		}
		catch (DataIntegrityViolationException ex) {
			// Only the external_id constraint means "duplicate"; any other violation is a real error.
			if (!isExternalIdConflict(ex)) {
				throw ex;
			}
			UUID existingId = repository.findIdByExternalId(request.externalId()).orElseThrow(() -> ex);
			throw new DuplicateTransactionException(request.externalId(), existingId);
		}
	}

	@Transactional(readOnly = true)
	public TransactionResponse get(String id) {
		return mapper.toResponse(load(id));
	}

	@Transactional(readOnly = true)
	public CursorPage<TransactionResponse> list(PageQuery page) {
		Limit limit = Limit.of(page.limit() + 1);
		List<Transaction> rows = page.after() == null ? repository.findAllByOrderByIdDesc(limit)
				: repository.findByIdLessThanOrderByIdDesc(page.after(), limit);
		return page.toPage(rows, Transaction::getId, mapper::toResponse);
	}

	/**
	 * Partial update: absent fields stay as stored; lists and metadata are replaced whole. Runs in one DB
	 * transaction under the row lock, so a concurrent PATCH sees either none or all of this change.
	 */
	@Transactional
	public TransactionResponse patch(String id, PatchTransactionRequest request) {
		Transaction transaction = lockUnbilled(id);
		// Identity fields may be repeated (clients often send the whole object) but never changed.
		rejectIdentityChange("type", request.type(), transaction.getType());
		rejectIdentityChange("external_id", request.externalId(), transaction.getExternalId());
		rejectIdentityChange("currency", request.currency(), transaction.getCurrency());

		if (request.metadata() != null) {
			TransactionMetadata metadata = metadataParser.parse(transaction.getType(), request.metadata());
			handlers.handlerFor(transaction.getType()).replaceMetadata(transaction, metadata);
		}
		if (request.occurredAt() != null) {
			transaction.changeOccurredAt(request.occurredAt());
		}
		if (request.total() != null) {
			transaction.changeTotal(request.total());
		}
		if (request.taxLines() != null) {
			transaction.replaceTaxLines(TransactionMapper.taxLines(request.taxLines()));
		}
		if (request.feeLines() != null) {
			transaction.replaceFeeLines(TransactionMapper.feeLines(request.feeLines()));
		}
		repository.flush();
		return mapper.toResponse(transaction);
	}

	@Transactional
	public void delete(String id) {
		repository.delete(lockUnbilled(id));
	}

	/**
	 * For invoicing: row-locks the transactions so no PATCH or DELETE can change them until the caller's
	 * transaction commits, then returns them in request order. Must run inside the caller's transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<TransactionResponse> lockForInvoicing(List<UUID> ids) {
		Set<UUID> locked = new HashSet<>(repository.lockByIds(ids));
		if (locked.size() != new HashSet<>(ids).size()) {
			Set<UUID> missing = new LinkedHashSet<>(ids);
			missing.removeAll(locked);
			throw new NotFoundException("Transaction", missing);
		}
		Map<UUID, Transaction> byId = repository.findAllById(ids)
			.stream()
			.collect(Collectors.toMap(Transaction::getId, Function.identity()));
		return ids.stream().map(byId::get).map(mapper::toResponse).toList();
	}

	private Transaction load(String id) {
		return Ids.parse(id).flatMap(repository::findById).orElseThrow(() -> new NotFoundException("Transaction", id));
	}

	/**
	 * Takes the row lock first, then checks invoice usage, so an invoice being created concurrently either
	 * finishes first (and we see its line) or waits until this change commits.
	 */
	private Transaction lockUnbilled(String id) {
		UUID uuid = Ids.parse(id).orElseThrow(() -> new NotFoundException("Transaction", id));
		if (repository.lockByIds(List.of(uuid)).isEmpty()) {
			throw new NotFoundException("Transaction", id);
		}
		if (invoiceUsage.isInvoiced(uuid)) {
			throw new TransactionInvoicedException(uuid);
		}
		return repository.findById(uuid).orElseThrow(() -> new NotFoundException("Transaction", id));
	}

	private static void rejectIdentityChange(String field, Object requested, Object stored) {
		if (requested != null && !Objects.equals(requested, stored)) {
			throw new BadRequestException(field + " cannot be changed");
		}
	}

	private static boolean isExternalIdConflict(DataIntegrityViolationException ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
					&& violation.getConstraintName() != null) {
				return EXTERNAL_ID_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName());
			}
			if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
				String message = String.valueOf(sql.getMessage());
				return message.contains(EXTERNAL_ID_CONSTRAINT);
			}
		}
		return false;
	}

}
