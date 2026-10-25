package com.koushik.invoice.invoicing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

	/** Row lock that serialises overlapping PATCH/DELETE of the same invoice. */
	@Query(value = "select id from invoices where id = :id for update", nativeQuery = true)
	Optional<UUID> lockById(@Param("id") UUID id);

	/** Served by {@code ix_invoice_lines_transaction_id}. */
	@Query(value = "select exists (select 1 from invoice_lines where transaction_id = :transactionId)",
			nativeQuery = true)
	boolean existsLineForTransaction(@Param("transactionId") UUID transactionId);

	List<Invoice> findAllByOrderByIdDesc(Limit limit);

	List<Invoice> findByIdLessThanOrderByIdDesc(UUID id, Limit limit);

}
