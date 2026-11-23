-- Refs #144. Order tables: customer_order, order_item, order_item_modifier.
-- Source: docs/erd.md v1.1, part "Users, roles, orders". The order table is customer_order,
-- because "order" is a reserved word in PostgreSQL (architecture.md section 4).
-- Needs: app_user (#62/#134), cafe and delivery_zone (#143), country, product, modifier (V1).
-- Money is numeric(12,2) in the local currency, rates are percents (15.00 = 15 %, Decision Log 5 Nov).
-- The order keeps every step of the price formula and copies of the names, so old orders and
-- reports do not change when prices, VAT or the menu change (erd.md, "Why it looks like this").

CREATE TABLE customer_order (
    id               BIGSERIAL     PRIMARY KEY,
    order_number     VARCHAR(20)   NOT NULL UNIQUE,                 -- WR- + 8 Crockford Base32
    user_id          BIGINT        REFERENCES app_user (id),        -- null for a guest
    country_id       BIGINT        NOT NULL REFERENCES country (id),
    order_date       DATE          NOT NULL,                        -- the day of the slot, local date
    scenario         VARCHAR(10)   NOT NULL,
    cafe_id          BIGINT        NOT NULL REFERENCES cafe (id),   -- pickup café, or the café of the zone
    delivery_zone_id BIGINT        REFERENCES delivery_zone (id),   -- null if pickup
    slot_start       TIME          NOT NULL,                        -- local time of the café
    slot_end         TIME          NOT NULL,
    delivery_address VARCHAR(255),
    contact_name     VARCHAR(150)  NOT NULL,
    contact_phone    VARCHAR(30)   NOT NULL,
    contact_email    VARCHAR(255),
    comment          VARCHAR(500),
    currency_code    CHAR(3)       NOT NULL REFERENCES currency (code),
    subtotal         NUMERIC(12,2) NOT NULL,
    vat_rate         NUMERIC(5,2)  NOT NULL,
    vat_amount       NUMERIC(12,2) NOT NULL,
    amount_incl_vat  NUMERIC(12,2) NOT NULL,
    discount_rate    NUMERIC(5,2)  NOT NULL,
    discount_amount  NUMERIC(12,2) NOT NULL,
    delivery_fee     NUMERIC(12,2) NOT NULL,
    total            NUMERIC(12,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'NEW',
    idempotency_key  UUID          UNIQUE,                          -- null without the header; many nulls are allowed
    request_hash     CHAR(64)      NOT NULL,                        -- SHA-256 of the request body, hex
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_order_scenario CHECK (scenario IN ('PICKUP', 'DELIVERY')),
    CONSTRAINT ck_customer_order_status
        CHECK (status IN ('NEW', 'CONFIRMED', 'DONE', 'CANCELLED')),
    -- DELIVERY has a zone and an address, PICKUP has neither
    CONSTRAINT ck_customer_order_delivery CHECK (
        (scenario = 'DELIVERY' AND delivery_zone_id IS NOT NULL AND delivery_address IS NOT NULL)
        OR (scenario = 'PICKUP' AND delivery_zone_id IS NULL)),
    CONSTRAINT ck_customer_order_slot CHECK (slot_end > slot_start),
    CONSTRAINT ck_customer_order_money CHECK (
        subtotal >= 0 AND vat_amount >= 0 AND amount_incl_vat >= 0
        AND discount_amount >= 0 AND delivery_fee >= 0 AND total >= 0),
    CONSTRAINT ck_customer_order_rates
        CHECK (vat_rate >= 0 AND discount_rate >= 0 AND discount_rate <= 100)
);

CREATE TABLE order_item (
    id           BIGSERIAL     PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    product_id   BIGINT        NOT NULL REFERENCES product (id),
    product_name VARCHAR(150)  NOT NULL,                            -- copy at order time
    quantity     INT           NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL,                            -- base price plus modifiers
    line_total   NUMERIC(12,2) NOT NULL,
    CONSTRAINT ck_order_item_quantity CHECK (quantity >= 1),
    CONSTRAINT ck_order_item_money CHECK (unit_price >= 0 AND line_total >= 0)
);

CREATE TABLE order_item_modifier (
    order_item_id BIGINT        NOT NULL REFERENCES order_item (id) ON DELETE CASCADE,
    modifier_id   BIGINT        NOT NULL REFERENCES modifier (id),
    modifier_name VARCHAR(150)  NOT NULL,                           -- copy at order time
    price_delta   NUMERIC(12,2) NOT NULL,
    PRIMARY KEY (order_item_id, modifier_id)                        -- each modifier once per line
);

-- Indexes (#144). The unique keys (order_number, idempotency_key) already have their own.
-- Reports by country and day (#66, #67, #68).
CREATE INDEX ix_customer_order_country_date ON customer_order (country_id, order_date);
-- "My orders": the orders of a logged-in user, newest first (#63).
CREATE INDEX ix_customer_order_user_created ON customer_order (user_id, created_at DESC);
-- Staff API: the orders of a café for a day.
CREATE INDEX ix_customer_order_cafe_date ON customer_order (cafe_id, order_date);
-- PostgreSQL does not index a foreign key by itself; the lines are always read with their order.
CREATE INDEX ix_order_item_order ON order_item (order_id);