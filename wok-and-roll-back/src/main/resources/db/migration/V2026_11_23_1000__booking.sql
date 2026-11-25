-- Refs #174. Table booking (api-contract.md section 3.10).
-- A booking takes one table of the café for 90 minutes. The server picks the smallest free table
-- with enough seats. The exclusion constraint makes double booking impossible in the database,
-- even when two requests arrive at the same moment.

-- btree_gist lets a GiST index compare table_id with "=" next to the range "&&".
-- It is a trusted extension (PostgreSQL 13+), so the database owner can create it.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE booking (
    id               BIGSERIAL     PRIMARY KEY,
    booking_number   VARCHAR(20)   NOT NULL UNIQUE,                 -- BK- + 8 Crockford Base32
    user_id          BIGINT        REFERENCES app_user (id),        -- null for a guest
    cafe_id          BIGINT        NOT NULL REFERENCES cafe (id),
    table_id         BIGINT        NOT NULL REFERENCES cafe_table (id),
    booking_date     DATE          NOT NULL,                        -- local date of the café
    booking_time     TIME          NOT NULL,                        -- local time of the café
    guests           INT           NOT NULL,
    during           TSTZRANGE     NOT NULL,                        -- [start, start + 90 min)
    contact_name     VARCHAR(150)  NOT NULL,
    contact_phone    VARCHAR(30)   NOT NULL,
    contact_email    VARCHAR(255),
    comment          VARCHAR(500),
    status           VARCHAR(20)   NOT NULL DEFAULT 'CONFIRMED',
    idempotency_key  UUID          UNIQUE,
    request_hash     CHAR(64)      NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_booking_guests CHECK (guests BETWEEN 1 AND 8),
    CONSTRAINT ck_booking_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT ex_booking_table_time
        EXCLUDE USING gist (table_id WITH =, during WITH &&) WHERE (status = 'CONFIRMED')
);

-- Staff API: the bookings of a café for a day (#176).
CREATE INDEX ix_booking_cafe_date ON booking (cafe_id, booking_date);
-- "My bookings" later: the bookings of a user.
CREATE INDEX ix_booking_user ON booking (user_id);
