package com.koushik.invoice.transaction.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	@Query("select t.id from Transaction t where t.externalId = :externalId")
	Optional<UUID> findIdByExternalId(@Param("externalId") String externalId);

	/**
	 * Row-locks the header rows ({@code FOR UPDATE}) in id order so concurrent lockers never deadlock. Done
	 * natively because Postgres rejects {@code FOR UPDATE} across the outer joins of a polymorphic load.
	 */
	@Query(value = "select id from transactions where id in (:ids) order by id for update", nativeQuery = true)
	List<UUID> lockByIds(@Param("ids") Collection<UUID> ids);

	/**
	 * Keyset pagination on the primary key (UUID v7 is time-ordered): every page is an index range scan with a
	 * LIMIT, so page 1,000 costs the same as page 1. Callers pass {@code limit + 1} to detect a next page.
	 */
	List<Transaction> findAllByOrderByIdDesc(Limit limit);

	List<Transaction> findByIdLessThanOrderByIdDesc(UUID id, Limit limit);

}
