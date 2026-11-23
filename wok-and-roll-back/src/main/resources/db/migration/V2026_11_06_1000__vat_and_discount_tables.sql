-- Refs #34. VAT by country and discount thresholds, read by the pricing service.
-- Depends on the `country` table from the base schema V1 (#32). After V1 we use timestamp versions.
-- Rates are in percent (15.00 = 15%, not 0.15), money is numeric(12,2), as in docs/erd.md.
-- Thresholds are per country, in the local currency (Decision Log).
-- No seed data here: VAT rates and threshold numbers go into the seed from #33.

CREATE TABLE vat_rate (
    id          BIGSERIAL PRIMARY KEY,
    country_id  BIGINT        NOT NULL UNIQUE REFERENCES country (id),
    rate        NUMERIC(5, 2) NOT NULL,
    updated_at  TIMESTAMP     NOT NULL DEFAULT now(),
    CONSTRAINT ck_vat_rate_range CHECK (rate >= 0 AND rate < 100)
);

CREATE TABLE discount_threshold (
    id             BIGSERIAL PRIMARY KEY,
    country_id     BIGINT         NOT NULL REFERENCES country (id),
    min_amount     NUMERIC(12, 2) NOT NULL,
    discount_rate  NUMERIC(5, 2)  NOT NULL,
    CONSTRAINT uq_discount_threshold UNIQUE (country_id, min_amount),
    CONSTRAINT ck_discount_min_amount CHECK (min_amount > 0),
    CONSTRAINT ck_discount_rate_range CHECK (discount_rate > 0 AND discount_rate <= 100)
);

COMMENT ON TABLE vat_rate IS 'One VAT rate per country, percent.';
COMMENT ON TABLE discount_threshold IS
    'Discount tiers per country. Rate = row with the biggest min_amount <= Amount incl. VAT, local currency.';