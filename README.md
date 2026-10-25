# trip-billing-service

Navan take-home B: a Spring Boot API that stores flight, hotel, rail and Navan-fee transactions and issues
invoices that are frozen snapshots of them. Postgres-backed; duplicate `external_id`s are rejected by a
database unique constraint, including under concurrent POSTs.

Design notes are in [ARCHITECTURE.md](ARCHITECTURE.md).

## Requirements

- Java 25 (`java -version`; on macOS `export JAVA_HOME=$(/usr/libexec/java_home -v 25)`)
- Docker running (the app starts Postgres from `compose.yaml`; the tests start their own with Testcontainers)

No API keys or secrets are needed.

## Run the API (one command)

```bash
./mvnw spring-boot:run
```

Spring Boot starts the Postgres container from `compose.yaml`, Flyway creates the schema, and the API listens
on `http://localhost:8080`. Health: `curl localhost:8080/actuator/health`.

Data survives restarts. To start from an empty database: `docker compose down -v`.

To use an existing Postgres instead, set `SPRING_DOCKER_COMPOSE_ENABLED=false` and
`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD`.

## Run the tests (one command)

```bash
./mvnw test
```

78 test runs (47 tests; race tests repeat), all over real HTTP against a throwaway Postgres.

| Class | What it covers |
| --- | --- |
| `RequiredScenariosTest` | The 10 scenarios from the brief, numbered the same way |
| `ValidationAndLifecycleTest` | Validation errors, PATCH/DELETE rules, invoice PATCH, pagination |
| `ConcurrencyEdgeCaseTest` | Races between *different* operations (below) |
| `DataEdgeCaseTest` | Boundary values and unusual-but-legal input (below) |
| `RollupCalculatorTest` | Rollup grouping and exact sums, as a unit test |
| `ArchitectureTest` | Layer and module dependency rules |

### Edge-case tests and why they exist

The required tests race only two identical POSTs. These cover the failures the design is meant to prevent. Each
test's Javadoc explains its purpose; race tests run 5–10 times because one passing run may simply not have overlapped.

| Test | Why it was added |
| --- | --- |
| Invoice create racing a PATCH of the same transaction (×10) | The riskiest race: an invoice must never copy a half-applied change. Invariant checked: the invoice line always equals the stored total. |
| DELETE racing an invoice create (×10) | Without the row lock both could "win", leaving an invoice line for a deleted row. Exactly one may succeed. |
| 8 concurrent PATCHes replacing `tax_lines` | The brief forbids a torn list; the stored list must be exactly one writer's list. |
| Two invoices locking the same rows in opposite order (×5) | Would deadlock if locks followed request order; they follow id order, so both succeed. |
| Invoice PATCH racing invoice DELETE (×5) | No half-rebuilt invoice or orphaned snapshot lines may remain. |
| 0.10 + 0.20 = 0.30 exactly | The float bug the brief warns about (`0.30000000000000004`). |
| Largest amount round-trips; one digit more is 400 | `NUMERIC(19,2)` limit must be exact (a double prints `1.0E17`) and overflow must be a validation error. |
| Invoice total that would overflow is 400 | **Found a real bug:** each amount was valid but their sum overflowed the column and surfaced as a misleading 409. Now checked in the `Invoice` aggregate before writing. |
| Unicode `external_id`; case-sensitive ids | Booking refs come from many systems; dedupe must be exact, with no silent case folding. |
| `external_id` of 128 vs 129 characters | Column limit must be a 400 from validation, not a database error. |
| Malformed JSON, array/string `metadata`, wrong field types | Broken clients must get 400, never 500 or a row with empty metadata. |
| Empty, ambiguous or `[null]` invoice bodies | The two body shapes have their own invalid combinations. |
| Hotel stay of zero nights | `check_out = check_in` must be rejected (DB `CHECK` is the backstop). |
| Snapshot freezes metadata, not only totals | Required test 4 only proves the total is frozen; a coupon changed after billing must not show on the invoice. |
| Frozen until the last invoice is deleted | With a transaction on two invoices, deleting one must not unfreeze it. |
| Invoice PATCH that swaps line order | Exercises the `DEFERRABLE` unique constraint; a plain one would fail mid-update. |
| 100-transaction invoice | Locking, snapshot and rollups stay exact and fast at a realistic trip size. |
| Pagination while rows are inserted mid-walk | Keyset pagination must not skip or repeat rows; offset pagination would. |

## API

| Method | Path | Success | Errors |
| --- | --- | --- | --- |
| POST | `/transactions` | 201 | 400 invalid body, unknown `type` or `fee_kind`; **409 duplicate `external_id`** (body has `existing_id`) |
| GET | `/transactions/{id}` | 200 | 404 |
| GET | `/transactions?limit=&cursor=` | 200 | 400 if `limit` is not 1–100 (default 50) |
| PATCH | `/transactions/{id}` | 200 | 404; **409 if on any invoice**; 400 to change `type`/`external_id`/`currency` |
| DELETE | `/transactions/{id}` | 204 | 404; **409 if on any invoice** |
| POST | `/invoices` | 201 | 404 unknown id; **409 mixed currency**; 409 same id twice |
| GET | `/invoices/{id}` | 200 | 404 |
| GET | `/invoices?limit=&cursor=` | 200 | 400 |
| PATCH | `/invoices/{id}` | 200 | 404; 409 mixed currency (invoice unchanged) |
| DELETE | `/invoices/{id}` | 204 | 404 (transactions are kept) |

- JSON is snake_case. Money is a JSON number with two decimals (`685.70`); more than two decimals is a 400.
- Errors are RFC 9457 `application/problem+json`.
- Lists return `{ "items": [...], "limit": 50, "next_cursor": "..." }`, newest first. Pass `next_cursor` as
  `cursor` for the next page; it is absent on the last page.
- **Duplicates return 409** with the existing `id` in `existing_id`, whatever the body says.
- **Same id twice on one invoice returns 409** (never silently deduplicated).

## Assumptions

Where the brief was silent, the service assumes the following. Each is a deliberate choice that is easy to change.

**Identity and tenancy**

- `external_id` is unique **across all types and all clients**: the brief asks for a unique index on `external_id`
  itself, and there is no auth or tenant concept to scope it by. A flight and a hotel cannot share one.
- A request whose `external_id` already exists is a duplicate **whatever its body says**; the stored row is never
  updated or merged by a POST (the fixture's `duplicate_flight` differs from `flight` and is still a duplicate).
- `type`, `external_id` and `currency` are a transaction's identity and never change after create.
- Server ids are UUIDs. Anything else in an id position (e.g. `txn_123`) is treated as "does not exist" (404).

**Money**

- `total` is the **gross amount and the source of truth**. `tax_lines` and `fee_lines` are a breakdown of what is
  already inside `total`, not extra charges added on top; the invoice grand total is the sum of `total`s, and the
  rollups explain it. The service does not check that fare + taxes + fees equals `total` (the rail fixture has no
  fare at all).
- Every amount has at most **2 decimals**, for every currency.
- All tax and fee lines are in the transaction's `currency`; there is no per-line currency.
- Totals and amounts are **zero or positive**; refunds and credit notes are not part of this task.
- A tax `rate` is optional and is a fraction (`0.07` = 7%). It is informational: the service never computes
  `amount` from `rate`.
- Hotel `extras` (e.g. breakfast) are descriptive only. Anything billable is expected in `fee_lines` or already in
  `total`.

**Types and metadata**

- New transactions need complete metadata: a flight needs at least one coupon, rail at least one leg, a hotel
  its name, full address and dates. Empty `{}` metadata is a 400.
- `fee_kind` is one of `trip_fee`, `agent_call_fee`. Adding a kind is a code + migration change, not runtime data.
- Hotel `nights` must equal `check_out − check_in`; flight and rail arrivals must be after departures.
- `country` is an ISO 3166 alpha-2 code in upper case (`GB`); `currency` is an ISO 4217 code known to Java.
- `related_external_id` on a Navan fee is a free reference; the booking it points to may not be in this service.
- Unknown JSON fields are ignored (the brief: "extra fields are fine").

**Time**

- Timestamps are **with an offset** (`2026-03-11T06:40:00Z` or `+01:00`). A timestamp without one is a
  400 rather than a guess at a time zone. Everything is stored and returned in UTC.
- Hotel `check_in` / `check_out` are plain dates (`2026-03-11`), local to the property.

**Invoices**

- An invoice is a **copy of the transactions at billing time**. Editing an invoiced transaction is blocked (409),
  and the invoice would not change even if the stored row did.
- A transaction may be on **more than one invoice** (e.g. the flight alone, then the whole trip).
- Invoices have no draft/issued state: any invoice can be PATCHed (rebuilt from current data) or deleted, as the
  brief requires. Once an invoice is deleted, its transactions can be edited again (unless another invoice
  still includes them).
- Lines keep the order of the ids in the request; rollups keep the order in which each tax or fee first appears.
- An invoice holds at least one transaction.

**Running**

- Reviewers have Java 25 and Docker. No external services, API keys or LLM calls are involved.
- One Postgres instance; single-region, no read replicas.

## Decisions the brief leaves open

| Question | Choice | Where it is enforced |
| --- | --- | --- |
| Duplicate `external_id` | **409 Conflict** with the existing row's id in `existing_id`. Checked before the body is validated, so a duplicate with a different (even invalid) body is still a 409. | `UNIQUE (external_id)` in the schema; `TransactionService.create` |
| Money | `BigDecimal` in Java, `NUMERIC(19,2)` in Postgres: **scale 2**. More than 2 decimals is a 400 (never rounded). Tax `rate` is `NUMERIC(9,6)`. JSON decimals are read as `BigDecimal`, never `double`. | `@Digits` on DTOs; `Money.normalize`; `V1__init.sql` |
| Tax rollup | Grouped by **(name, rate)**, so VAT 7% and VAT 19% stay separate; ordered by first appearance | `RollupCalculator` |
| Fee rollup | Grouped by **name**, ordered by first appearance | `RollupCalculator` |
| Same transaction id twice on one invoice | **409**, nothing written (not silently deduplicated) | `InvoiceService`; `UNIQUE (invoice_id, transaction_id)` |
| Ids that are not UUIDs (e.g. `txn_123`) | **404**: they cannot exist | `Ids.parse` |
| Transaction PATCH | Absent fields unchanged; `tax_lines`, `fee_lines`, `metadata` replaced whole; changing `type`, `external_id` or `currency` is a 400 | `TransactionService.patch` |
| Overlapping PATCH/DELETE on one id | Serialised by a row lock: last write wins, but a write is never half-applied | `lockByIds` / `lockById` (`SELECT … FOR UPDATE`) |
| `limit` above 100 | **400** (not silently capped) | `PageQuery` |
| Hotel `nights` | Must equal `check_out − check_in`, else 400 | `HotelHandler` |

## Database and volume

These are in the schema (`src/main/resources/db/migration/V1__init.sql`), not only in comments:

- **Unique index on `external_id`** (`uk_transactions_external_id`): the database, not the application, guarantees
  one row per `external_id`, including when two POSTs race.
- **Primary keys** on `transactions.id` and `invoices.id` make `GET /transactions/{id}` and `GET /invoices/{id}`
  single index lookups. Ids are UUID v7, so they are time-ordered.
- **`ix_invoice_lines_transaction_id`** makes "is this transaction on an invoice?" (checked on every transaction
  PATCH/DELETE) an index lookup.
- **`UNIQUE (invoice_id, transaction_id)`** on `invoice_lines` prevents counting a transaction twice on one invoice.
- **`invoice_lines.transaction_id` is a foreign key `ON DELETE RESTRICT`**: Postgres itself refuses to delete a
  transaction that is on an invoice.
- **Lists always use `LIMIT`**: keyset pagination `WHERE id < :cursor ORDER BY id DESC LIMIT :limit + 1`. Default
  50, maximum 100. Nothing is loaded whole and paginated in memory. Child rows for a page are fetched in batches
  of up to 100, not one query per row.

## What can go wrong, and known limitations

**Handled failure modes** (each has a test; see `ARCHITECTURE.md`, section High volume, for detail):

| Scenario | Without protection | What happens here |
| --- | --- | --- |
| Two POSTs with the same `external_id` at the same moment | Both insert | Unique constraint: one 201, the other 409 with the winner's id (`RequiredScenariosTest#6`) |
| Client retries a POST after a timeout | Second row with a new id | Same unique key: 409 with the original id |
| Transaction edited after it was invoiced | Invoice total changes | PATCH/DELETE return 409; the invoice holds its own copy anyway (`#4`, `#7`, `#8`) |
| Invoice created while the same transaction is being PATCHed | Invoice copies a half-applied change | Both take the same row lock; the PATCH either finishes first or then sees the invoice and returns 409 |
| Two invoices locking the same transactions | Deadlock | Locks are always taken in id order |
| Request asks for 1,000,000 rows | Memory exhaustion | `limit` is capped at 100 (400 above that) |
| Invoice mixes EUR and USD | Meaningless total | 409, nothing written (`#5`) |
| Transaction deleted directly in SQL while invoiced | Dangling invoice line | Foreign key `RESTRICT` refuses the delete |

**Known limitations** (deliberate, out of scope for this task):

- One transaction may appear on **several invoices**, so double billing across invoices is possible. The brief says
  "on *any* invoice", and blocking it would stop invoicing a flight alone and then the whole trip.
- Money scale is fixed at 2 decimals, so zero-decimal (JPY) and three-decimal (BHD, KWD) currencies are not
  modelled exactly.
- `total` is the source of truth; the service does not check that fare + taxes + fees adds up to it (the rail
  fixture has no fare).
- `total` must be zero or positive; refunds and credit notes are not modelled.
- `related_external_id` on a Navan fee is not checked against stored bookings (the booking may live elsewhere).
- A changed body under a known `external_id` is a 409, never an update; there is no request hashing to return 200
  for an exact replay.
- Invoices have no status (draft / issued / void) and no human-readable number; any invoice can be PATCHed.
- Postgres is a single instance; read replicas or partitioning are not set up.

## Example curls

The examples post the brief's fixtures (copied to `src/test/resources/fixtures/task-b/`) and use `jq`.

```bash
B=http://localhost:8080
S=src/test/resources/fixtures/task-b/seed-payloads.json

# Create one of each type
for k in flight hotel rail trip_fee agent_call_fee; do
  jq ".$k" $S | curl -s -X POST $B/transactions -H 'Content-Type: application/json' -d @- | jq -c '{id, type, external_id}'
done

# Duplicate external_id: the same flight twice -> 409 with existing_id; still one row
jq '.flight' $S | curl -s -X POST $B/transactions -H 'Content-Type: application/json' -d @- | jq .
jq '.duplicate_flight | del(.comment)' $S | curl -s -X POST $B/transactions -H 'Content-Type: application/json' -d @- | jq .
docker compose exec postgres psql -U invoice -d invoice \
  -c "select count(*) from transactions where external_id = 'flight-TXL-LHR-2026-03-11'"

# List and get
curl -s "$B/transactions?limit=5" | jq '.items[] | {id, type}'
FLIGHT=$(curl -s "$B/transactions?limit=5" | jq -r '.items[] | select(.type=="flight") | .id')
HOTEL=$(curl -s "$B/transactions?limit=5"  | jq -r '.items[] | select(.type=="hotel") | .id')
curl -s $B/transactions/$FLIGHT | jq .

# Patch and delete a transaction that is not invoiced yet
curl -s -X PATCH $B/transactions/$HOTEL -H 'Content-Type: application/json' \
  -d '{"tax_lines":[{"name":"VAT","rate":0.07,"amount":21.70}]}' | jq -c '{id, total, tax_lines}'
RAIL2=$(jq '.rail | .external_id = "rail-to-delete"' $S | curl -s -X POST $B/transactions -H 'Content-Type: application/json' -d @- | jq -r .id)
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $B/transactions/$RAIL2          # 204

# Invoice the whole trip (one id also works: {"transaction_id": "..."})
IDS=$(curl -s "$B/transactions?limit=5" | jq -c '[.items[].id]')
INVOICE=$(curl -s -X POST $B/invoices -H 'Content-Type: application/json' -d "{\"transaction_ids\": $IDS}" | jq -r .id)
curl -s $B/invoices/$INVOICE | jq '{totals, tax_rollup, fee_rollup}'                 # grand_total 685.70

# Invoiced transactions are frozen: PATCH and DELETE both return 409
curl -s -X PATCH $B/transactions/$FLIGHT -H 'Content-Type: application/json' -d '{"total": 300}' | jq .
curl -s -X DELETE $B/transactions/$FLIGHT | jq .

# List, patch (rebuild snapshot with a new set of ids) and delete invoices
curl -s "$B/invoices?limit=10" | jq '.items[] | {id, grand_total: .totals.grand_total}'
curl -s -X PATCH $B/invoices/$INVOICE -H 'Content-Type: application/json' \
  -d "{\"transaction_ids\": [\"$FLIGHT\", \"$HOTEL\"]}" | jq '.totals'               # 551.70
curl -s -o /dev/null -w '%{http_code}\n' -X DELETE $B/invoices/$INVOICE            # 204
curl -s -o /dev/null -w '%{http_code}\n' $B/transactions/$FLIGHT                   # 200, still there
```

## Project layout

```
src/main/java/com/koushik/invoice/
  common/        errors (ProblemDetail), cursor pagination, validation, money scale
  transaction/   api -> application (service, per-type handlers, DTOs) -> domain (entities, repository)
  invoicing/     api -> application -> domain (Invoice aggregate, rollups); infrastructure (port adapter)
src/main/resources/db/migration/V1__init.sql   the whole schema
```
