-- Refs #32. Base schema: currencies, countries, catalog, regional prices, modifiers, stock.
-- Source: docs/erd.md, part "Country, currency, VAT, discounts, catalog, pricing".
-- NOT here: vat_rate and discount_threshold (V2026_11_06_1000, #34), users/roles/orders,
-- pickup points, delivery zones, time slots (other parts of the ERD).
-- No seed data: it goes into the seed from #33.
-- Money is numeric(12,2), local currency, before VAT.

CREATE TABLE currency (
    code  CHAR(3)      PRIMARY KEY,
    name  VARCHAR(100) NOT NULL
);

CREATE TABLE country (
    id             BIGSERIAL    PRIMARY KEY,
    code           CHAR(3)      NOT NULL UNIQUE,
    name           VARCHAR(100) NOT NULL,
    currency_code  CHAR(3)      NOT NULL REFERENCES currency (code)
);
CREATE INDEX ix_country_currency ON country (currency_code);

CREATE TABLE category (
    id          BIGSERIAL    PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    sort_order  INT          NOT NULL DEFAULT 0,
    visible     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE product (
    id           BIGSERIAL    PRIMARY KEY,
    category_id  BIGINT       NOT NULL REFERENCES category (id),
    name         VARCHAR(150) NOT NULL,
    description  VARCHAR(1000),
    image_url    VARCHAR(500),
    visible      BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX ix_product_category ON product (category_id);

-- One row per product and country. No row = the dish is not sold in that country.
CREATE TABLE regional_price (
    id          BIGSERIAL      PRIMARY KEY,
    product_id  BIGINT         NOT NULL REFERENCES product (id),
    country_id  BIGINT         NOT NULL REFERENCES country (id),
    price       NUMERIC(12, 2) NOT NULL,
    in_stock    BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_regional_price UNIQUE (product_id, country_id),
    CONSTRAINT ck_regional_price_range CHECK (price >= 0)
);
CREATE INDEX ix_regional_price_country ON regional_price (country_id);

CREATE TABLE modifier (
    id          BIGSERIAL    PRIMARY KEY,
    product_id  BIGINT       NOT NULL REFERENCES product (id),
    type        VARCHAR(10)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    visible     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_modifier_type CHECK (type IN ('SIZE', 'EXTRA'))
);
CREATE INDEX ix_modifier_product ON modifier (product_id);

-- Price change of a modifier per country. Can be 0.
CREATE TABLE modifier_price (
    id           BIGSERIAL      PRIMARY KEY,
    modifier_id  BIGINT         NOT NULL REFERENCES modifier (id),
    country_id   BIGINT         NOT NULL REFERENCES country (id),
    price_delta  NUMERIC(12, 2) NOT NULL,
    CONSTRAINT uq_modifier_price UNIQUE (modifier_id, country_id),
    CONSTRAINT ck_modifier_price_range CHECK (price_delta >= 0)
);
CREATE INDEX ix_modifier_price_country ON modifier_price (country_id);
