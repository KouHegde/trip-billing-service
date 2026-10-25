# Architecture

Spring Boot 4.1 / Java 25 on Postgres 17. A modular monolith with two feature modules, `transaction` and
`invoicing`, each split into `api` → `application` → `domain`. Every rule that protects money is enforced by
the database first and the code second.

## Seams

| Concern | Where |
| --- | --- |
| HTTP | `*/api/*Controller` (thin: bind, validate, delegate) |
| Use cases and DB transaction boundaries | `TransactionService`, `InvoiceService` (`@Transactional` only here) |
| Per-type behaviour (flight, hotel, rail, navan_fee) | `TransactionTypeHandler` strategies, chosen by `TransactionHandlerRegistry` |
| Transaction persistence | `Transaction` (JPA `JOINED` hierarchy) + `TransactionRepository` |
| Duplicate identity | `UNIQUE (external_id)` + conflict handling in `TransactionService.create` |
| Invoice snapshot and rollups | `Invoice.issue/reissue` (aggregate), `RollupCalculator` |
| Errors | `GlobalExceptionHandler` → RFC 9457 problem bodies |

Dependencies run `invoicing → transaction` only. The transaction module must know "is this transaction on an
invoice?", so it declares the `InvoiceUsageChecker` port and the invoicing module implements it. `ArchitectureTest`
fails the build if a layer or module rule is broken.

## Tables and models

**Shared header + table per type.** `transactions` holds what every type shares: `id` (UUID v7),
`type`, `external_id`, `currency`, `occurred_at`, `total`, `version` and timestamps. Each type has its own table
sharing that primary key, so type fields are real `NOT NULL` / `CHECK`ed columns:

| Type | Tables |
| --- | --- |
| flight | `flight_transactions` (origin, destination, fare) + `flight_coupons` (carrier, flight_number, from/to, departure/arrival; one row per segment) |
| hotel | `hotel_transactions` (name, address street/city/postal_code/country, check_in/out, nights, room rate) + `hotel_extras` |
| rail | `rail_transactions` + `rail_legs` (origin, destination, times, train_number, class; a change of train is two legs) |
| navan_fee | `navan_fee_transactions` (`fee_kind` CHECK IN `trip_fee`, `agent_call_fee`; description; related_external_id) |

**Taxes vs fees** are two tables, `transaction_tax_lines` (name, rate, amount) and `transaction_fee_lines`
(name, amount), never a mixed "extras" column. Lines, coupons, legs and extras are value objects (`@Embeddable`
records with an order column) and are replaced as a whole list inside one DB transaction, so a list is never
half-written.

Rejected alternatives: one table with a JSON `metadata` blob (the DB cannot enforce "hotel name required"), a
single wide table (mostly NULL, no `NOT NULL` per type), and a table per concrete type (no single unique index
on `external_id`).

**Money** is `BigDecimal` / `NUMERIC(19,2)`, scale 2. Input with more than two decimals is a 400, so nothing is
ever rounded. Jackson reads every JSON decimal as `BigDecimal` (including inside `metadata`). Tax rates are
`NUMERIC(9,6)`.

## external_id uniqueness and concurrent POSTs

`external_id` is the client's booking reference: the only key a retried or duplicated POST shares, because our
`id` is generated per insert. `transactions.external_id` has a unique constraint (`uk_transactions_external_id`).

`create` first looks the `external_id` up (fast path: 409 with `existing_id`, before the body's metadata is even
validated, so the fixture's `duplicate_flight` with empty coupons is still a duplicate). Two overlapping POSTs can
both pass that lookup; the second `INSERT` then blocks on the unique index and fails with SQLState 23505 when the
first commits. That error is mapped to the same 409 with the winner's id. The check-then-insert is only an
optimisation; the constraint is the guarantee. `RequiredScenariosTest#6` fires 8 concurrent POSTs and asserts
exactly one 201 and one row.

The header, type row, tax lines, fee lines and child rows are written in one DB transaction (all or nothing).

## Invoice snapshot

`invoice_lines` copies each transaction at billing time: real columns for what is summed or queried
(`transaction_id`, `transaction_type`, `fee_kind`, `external_id`, `occurred_at`, `currency`, `total`) and a
`snapshot JSONB` holding the full transaction exactly as `GET /transactions/{id}` returned it (metadata, tax and fee
lines). Invoice reads never join the live `transactions` table for content, so later changes cannot alter an
issued invoice. Tax rollup (grouped by name + rate) and fee rollup (grouped by name) are computed once and stored
in `invoice_tax_rollups` / `invoice_fee_rollups`, so a later change to rollup code cannot change an old invoice
either. `RequiredScenariosTest#4` rewrites the stored flight total in SQL and checks the invoice still says 220.00.

Rules: one currency per invoice (409 otherwise, nothing written); the same id twice in one request is a 409;
`invoice_lines` has `UNIQUE (invoice_id, transaction_id)` (deferred to commit so a PATCH can reorder lines). A
transaction may appear on several invoices; double billing across invoices is a known limitation.

## Update and delete safety

- PATCH/DELETE of a transaction takes a row lock (`SELECT … FOR UPDATE`), then checks
  `invoice_lines.transaction_id`; if invoiced → 409 and nothing changes.
- Invoice create/PATCH locks the listed transactions (`FOR UPDATE`, ordered by id so two invoices cannot
  deadlock) before copying them. So a transaction PATCH either commits before the snapshot is taken, or waits and
  then sees the invoice line and returns 409. A snapshot can never capture a half-applied change.
- `invoice_lines.transaction_id` is a foreign key `ON DELETE RESTRICT`: even if code were bypassed, Postgres
  refuses to delete a billed transaction.
- Overlapping PATCH/DELETE on the same invoice serialise on the invoice's row lock. Lists are replaced inside one
  DB transaction, so the outcome is last-write-wins but always whole. `@Version` columns are a backstop and surface
  as 409.

## High volume

What breaks first under concurrent PATCH + duplicate POST + large lists, and what is in place:

1. **Duplicate POSTs racing.** Without a constraint both inserts succeed. Fixed by the unique index on
   `external_id`; the losing insert waits on the index only for that one key, and becomes a 409.
2. **Unbounded lists.** A `SELECT *` grows with the table and eventually exhausts memory. Every list is keyset
   paginated: `WHERE id < :cursor ORDER BY id DESC LIMIT :limit+1` on the primary key (UUID v7 is time-ordered), so
   page 1,000 costs the same as page 1. `limit` defaults to 50; above 100 is a 400. Nothing is paginated in memory.
   Child collections for a page load in batches (`hibernate.default_batch_fetch_size=100`), not one query per row.
3. **"Is it invoiced?" on every PATCH/DELETE.** It is an `EXISTS` on `ix_invoice_lines_transaction_id`, not a
   scan.
4. **Lock contention.** Row locks are per transaction / per invoice, held only for one short DB transaction, and
   always taken in id order. Hot spots appear only when many invoices bill the same transaction at once.
5. **Lookups by id.** `GET /transactions/{id}` and `GET /invoices/{id}` are primary-key lookups; child tables are
   keyed by `(parent_id, line_no)`.

Next steps at higher scale (not built): a connection pool sized to Postgres cores, read replicas for the list
endpoints, and partitioning `transactions` by `occurred_at`.
