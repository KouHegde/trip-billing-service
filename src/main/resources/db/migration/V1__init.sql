-- Money is NUMERIC(19,2) everywhere (scale 2); tax rates are NUMERIC(9,6).
--
-- Transactions use a shared header table plus one table per type (JPA JOINED inheritance): shared identity and
-- money live in one place with one unique index, while type-specific fields stay real, constrained columns.
-- Every child table cascades on delete; only invoice_lines -> transactions is RESTRICT (see below).

-- Header shared by all four types. `type` is the JPA discriminator.
CREATE TABLE transactions (
    id           UUID PRIMARY KEY,
    type         VARCHAR(16)   NOT NULL CHECK (type IN ('flight', 'hotel', 'rail', 'navan_fee')),
    external_id  VARCHAR(128)  NOT NULL,
    currency     VARCHAR(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    occurred_at  TIMESTAMPTZ   NOT NULL,
    total        NUMERIC(19,2) NOT NULL,
    version      BIGINT        NOT NULL,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    -- The duplicate guarantee: two concurrent POSTs with one external_id cannot both insert. The application maps a
    -- violation of this constraint (by name) to 409 with the existing id.
    CONSTRAINT uk_transactions_external_id UNIQUE (external_id)
);

-- Taxes and fees are separate tables (not one "extras" column): only taxes have a rate, and the invoice rolls
-- them up separately. line_no keeps the client's order.

CREATE TABLE transaction_tax_lines (
    transaction_id UUID          NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    line_no        INT           NOT NULL,
    name           VARCHAR(128)  NOT NULL,
    rate           NUMERIC(9,6),
    amount         NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (transaction_id, line_no)
);

CREATE TABLE transaction_fee_lines (
    transaction_id UUID          NOT NULL REFERENCES transactions (id) ON DELETE CASCADE,
    line_no        INT           NOT NULL,
    name           VARCHAR(128)  NOT NULL,
    amount         NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (transaction_id, line_no)
);

-- One row per segment; a round trip has several coupons.
CREATE TABLE flight_transactions (
    transaction_id UUID PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    origin         VARCHAR(64)   NOT NULL,
    destination    VARCHAR(64)   NOT NULL,
    fare           NUMERIC(19,2) NOT NULL
);

CREATE TABLE flight_coupons (
    transaction_id UUID         NOT NULL REFERENCES flight_transactions (transaction_id) ON DELETE CASCADE,
    coupon_no      INT          NOT NULL,
    carrier        VARCHAR(8),
    flight_number  VARCHAR(16)  NOT NULL,
    from_airport   VARCHAR(64)  NOT NULL,
    to_airport     VARCHAR(64)  NOT NULL,
    departure_at   TIMESTAMPTZ  NOT NULL,
    arrival_at     TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (transaction_id, coupon_no)
);

CREATE TABLE hotel_transactions (
    transaction_id      UUID PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    name                VARCHAR(256)  NOT NULL,
    address_street      VARCHAR(256)  NOT NULL,
    address_city        VARCHAR(128)  NOT NULL,
    address_postal_code VARCHAR(32),
    address_country     VARCHAR(2)    NOT NULL,
    check_in            DATE          NOT NULL,
    check_out           DATE          NOT NULL,
    nights              INT           NOT NULL CHECK (nights > 0),
    room_rate_per_night NUMERIC(19,2) NOT NULL,
    CONSTRAINT ck_hotel_dates CHECK (check_out > check_in)
);

-- Informational only (e.g. breakfast); billed amounts belong in transaction_fee_lines, so extras never add to totals.
CREATE TABLE hotel_extras (
    transaction_id UUID          NOT NULL REFERENCES hotel_transactions (transaction_id) ON DELETE CASCADE,
    line_no        INT           NOT NULL,
    name           VARCHAR(128)  NOT NULL,
    amount         NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (transaction_id, line_no)
);

CREATE TABLE rail_transactions (
    transaction_id UUID PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE
);

-- A journey with a change of train is two legs, not a single origin/destination pair.
CREATE TABLE rail_legs (
    transaction_id UUID         NOT NULL REFERENCES rail_transactions (transaction_id) ON DELETE CASCADE,
    leg_no         INT          NOT NULL,
    origin         VARCHAR(128) NOT NULL,
    destination    VARCHAR(128) NOT NULL,
    departure_at   TIMESTAMPTZ  NOT NULL,
    arrival_at     TIMESTAMPTZ  NOT NULL,
    train_number   VARCHAR(32),
    travel_class   VARCHAR(32),
    PRIMARY KEY (transaction_id, leg_no)
);

CREATE TABLE navan_fee_transactions (
    transaction_id      UUID PRIMARY KEY REFERENCES transactions (id) ON DELETE CASCADE,
    fee_kind            VARCHAR(32)  NOT NULL CHECK (fee_kind IN ('trip_fee', 'agent_call_fee')),
    description         VARCHAR(512),
    -- Not a foreign key: the booking this fee belongs to may not be stored in this service.
    related_external_id VARCHAR(128)
);

CREATE TABLE invoices (
    id          UUID PRIMARY KEY,
    currency    VARCHAR(3)    NOT NULL,
    grand_total NUMERIC(19,2) NOT NULL,
    version     BIGINT        NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL
);

-- Snapshot lines: money columns are real, the full transaction is frozen in `snapshot`.
-- RESTRICT makes the database refuse to delete a transaction that is on an invoice.
CREATE TABLE invoice_lines (
    invoice_id       UUID          NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    line_no          INT           NOT NULL,
    transaction_id   UUID          NOT NULL REFERENCES transactions (id) ON DELETE RESTRICT,
    transaction_type VARCHAR(16)   NOT NULL,
    fee_kind         VARCHAR(32),
    external_id      VARCHAR(128)  NOT NULL,
    occurred_at      TIMESTAMPTZ   NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    total            NUMERIC(19,2) NOT NULL,
    snapshot         JSONB         NOT NULL,
    PRIMARY KEY (invoice_id, line_no),
    -- Deferred so an invoice PATCH can rewrite lines in any order within one transaction.
    CONSTRAINT uk_invoice_lines_transaction UNIQUE (invoice_id, transaction_id) DEFERRABLE INITIALLY DEFERRED
);

-- "Is this transaction on any invoice?" before PATCH/DELETE of a transaction.
CREATE INDEX ix_invoice_lines_transaction_id ON invoice_lines (transaction_id);

-- Rollups are stored when the invoice is issued, so a later change to rollup code cannot alter an old invoice.
CREATE TABLE invoice_tax_rollups (
    invoice_id UUID          NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    line_no    INT           NOT NULL,
    name       VARCHAR(128)  NOT NULL,
    rate       NUMERIC(9,6),
    amount     NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (invoice_id, line_no)
);

CREATE TABLE invoice_fee_rollups (
    invoice_id UUID          NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    line_no    INT           NOT NULL,
    name       VARCHAR(128)  NOT NULL,
    amount     NUMERIC(19,2) NOT NULL,
    PRIMARY KEY (invoice_id, line_no)
);
