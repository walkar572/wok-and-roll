-- Refs #171. Sales history for the reports demo: 1 Jun - 22 Nov 2026, all 5 countries.
--
-- Deterministic: every "random" choice comes from md5 of a fixed text key, so every database
-- gets exactly the same orders, numbers and order numbers (QA compares the reports with SQL).
-- Formula-consistent: every order is priced with the rules of api-contract.md section 5 from the
-- seeded regional prices, VAT rates, discount thresholds and zone fees:
--   line_total = unit_price * quantity; subtotal = sum(line_total); vat = round(subtotal * rate)
--   amount = subtotal + vat; discount = round(amount * tier rate); total = amount - discount + fee
-- PostgreSQL round(numeric, 2) rounds half away from zero, which is HALF_UP for these positive
-- values, the same as the backend.
--
-- Only dishes without SIZE modifiers and in stock are used, so every line is valid as it is.
-- No slot places are taken (all dates are in the past), so time slots are not affected.
-- The report integration tests use 2024 and 2031, so this history does not change them.
-- About 1 order in 60 belongs to the demo customer, so "My orders" has a history to show.

-- 1. Orders: country x day x n, with more orders later in the year, at weekends and in the
--    bigger markets (about 3,400 orders in all).
CREATE TEMP TABLE seed_order AS
WITH day_country AS (
    SELECT d::date AS day, c.id AS country_id, c.code, c.currency_code, c.time_zone,
           vr.rate AS vat_rate
    FROM generate_series(DATE '2026-06-01', DATE '2026-11-22', INTERVAL '1 day') AS d
    CROSS JOIN country c
    JOIN vat_rate vr ON vr.country_id = c.id
),
numbered AS (
    SELECT dc.*, n
    FROM day_country dc
    CROSS JOIN LATERAL generate_series(
        1,
        1
        + ('x' || substr(md5('wr-seed-day-' || dc.code || dc.day), 1, 7))::bit(28)::int % 3
        + (extract(MONTH FROM dc.day)::int - 6) / 2
        + CASE WHEN extract(ISODOW FROM dc.day) >= 6 THEN 1 ELSE 0 END
        -- bigger markets get more orders, so the region chart has a story
        + CASE dc.code WHEN 'UZB' THEN 2 WHEN 'KAZ' THEN 1 WHEN 'CHN' THEN 1 ELSE 0 END
    ) AS n
),
keyed AS (
    SELECT nb.*, md5('wr-seed-order-' || nb.code || '-' || nb.day || '-' || nb.n) AS h
    FROM numbered nb
)
SELECT
    -- WR- and 8 hex digits: hex is a subset of the Crockford Base32 alphabet of real numbers
    'WR-' || upper(substr(k.h, 1, 8)) AS order_number,
    k.country_id, k.code, k.currency_code, k.time_zone, k.vat_rate, k.day AS order_date, k.h,
    ('x' || substr(k.h, 9, 7))::bit(28)::int  AS h_scenario,
    ('x' || substr(k.h, 16, 7))::bit(28)::int AS h_place,
    ('x' || substr(k.h, 23, 7))::bit(28)::int AS h_misc
FROM keyed k;

-- 2. Where and when: pickup at one of the 2 cafés, or delivery to one of the 3 zones (1 in 3).
CREATE TEMP TABLE seed_place AS
WITH cafes AS (
    SELECT cf.id, cf.country_id,
           row_number() OVER (PARTITION BY cf.country_id ORDER BY cf.id) - 1 AS idx,
           count(*) OVER (PARTITION BY cf.country_id) AS cnt
    FROM cafe cf WHERE cf.active
),
zones AS (
    SELECT z.id, z.country_id, z.cafe_id, z.fee,
           row_number() OVER (PARTITION BY z.country_id ORDER BY z.id) - 1 AS idx,
           count(*) OVER (PARTITION BY z.country_id) AS cnt
    FROM delivery_zone z WHERE z.active
)
SELECT so.order_number,
       CASE WHEN so.h_scenario % 3 = 0 THEN 'DELIVERY' ELSE 'PICKUP' END AS scenario,
       CASE WHEN so.h_scenario % 3 = 0 THEN z.cafe_id ELSE cf.id END AS cafe_id,
       CASE WHEN so.h_scenario % 3 = 0 THEN z.id END AS delivery_zone_id,
       CASE WHEN so.h_scenario % 3 = 0 THEN z.fee ELSE 0.00 END AS delivery_fee,
       TIME '10:00' + (so.h_misc % 23) * INTERVAL '30 minutes' AS slot_start
FROM seed_order so
JOIN cafes cf ON cf.country_id = so.country_id AND cf.idx = so.h_place % cf.cnt
JOIN zones z ON z.country_id = so.country_id AND z.idx = so.h_place % z.cnt;

-- 3. Lines: 1-3 different dishes, quantity 1-3, at the regional price of the order's country.
CREATE TEMP TABLE seed_line AS
WITH dishes AS (
    SELECT p.id, p.name,
           row_number() OVER (ORDER BY p.id) - 1 AS idx,
           count(*) OVER () AS cnt
    FROM product p
    WHERE p.visible
      AND NOT EXISTS (SELECT 1 FROM modifier m WHERE m.product_id = p.id AND m.type = 'SIZE')
      AND EXISTS (SELECT 1 FROM regional_price rp WHERE rp.product_id = p.id AND rp.in_stock)
),
lines AS (
    SELECT so.order_number, so.country_id, so.h, ln,
           ('x' || substr(md5(so.h || '-line-' || ln), 1, 7))::bit(28)::int AS h_line,
           ('x' || substr(so.h, 30, 3))::bit(12)::int AS h_first
    FROM seed_order so
    CROSS JOIN LATERAL generate_series(1, 1 + so.h_misc % 3) AS ln
)
SELECT l.order_number, l.ln, d.id AS product_id, d.name AS product_name,
       1 + l.h_line % 3 AS quantity,
       rp.price AS unit_price,
       rp.price * (1 + l.h_line % 3) AS line_total
FROM lines l
JOIN dishes d ON d.idx = (l.h_first + l.ln) % d.cnt
JOIN regional_price rp ON rp.product_id = d.id AND rp.country_id = l.country_id;

-- 4. Totals with the formula of api-contract.md section 5.
CREATE TEMP TABLE seed_total AS
WITH sub AS (
    SELECT order_number, sum(line_total) AS subtotal FROM seed_line GROUP BY order_number
),
vat AS (
    SELECT so.order_number, s.subtotal, so.vat_rate,
           round(s.subtotal * so.vat_rate / 100, 2) AS vat_amount
    FROM seed_order so JOIN sub s ON s.order_number = so.order_number
),
amount AS (
    SELECT v.*, v.subtotal + v.vat_amount AS amount_incl_vat FROM vat v
),
rate AS (
    SELECT a.*,
           coalesce((SELECT max(dt.discount_rate)
                     FROM discount_threshold dt
                     JOIN seed_order so ON so.country_id = dt.country_id
                     WHERE so.order_number = a.order_number
                       AND dt.min_amount <= a.amount_incl_vat), 0.00) AS discount_rate
    FROM amount a
)
SELECT r.*, round(r.amount_incl_vat * r.discount_rate / 100, 2) AS discount_amount
FROM rate r;

-- 5. The orders. Most are DONE; the last days are NEW or CONFIRMED; 1 in 25 is CANCELLED.
INSERT INTO customer_order (
    order_number, user_id, country_id, order_date, scenario, cafe_id, delivery_zone_id,
    slot_start, slot_end, delivery_address, contact_name, contact_phone, contact_email, comment,
    currency_code, subtotal, vat_rate, vat_amount, amount_incl_vat, discount_rate,
    discount_amount, delivery_fee, total, status, idempotency_key, request_hash,
    created_at, updated_at)
SELECT
    so.order_number,
    CASE WHEN so.h_misc % 60 = 7
         THEN (SELECT id FROM app_user WHERE email = 'customer@wokandroll.test') END,
    so.country_id, so.order_date, sp.scenario, sp.cafe_id, sp.delivery_zone_id,
    sp.slot_start, sp.slot_start + INTERVAL '30 minutes',
    CASE WHEN sp.scenario = 'DELIVERY'
         THEN (1 + so.h_place % 120) || ' Demo Street, apt ' || (1 + so.h_misc % 80) END,
    (ARRAY['Aziz Karimov', 'Dina Sultanova', 'Nino Beridze', 'Olena Shevchuk', 'Li Wei',
           'Timur Rakhimov', 'Aigerim Nurlanova', 'Giorgi Kapanadze', 'Andrii Bondar',
           'Mei Chen'])[1 + so.h_misc % 10],
    CASE so.code
        WHEN 'UZB' THEN '+99890'
        WHEN 'KAZ' THEN '+7701'
        WHEN 'GEO' THEN '+99559'
        WHEN 'UKR' THEN '+38067'
        ELSE '+86138'
    END || lpad((so.h_place % 10000000)::text, 7, '0'),
    NULL, NULL,
    so.currency_code, st.subtotal, st.vat_rate, st.vat_amount, st.amount_incl_vat,
    st.discount_rate, st.discount_amount, sp.delivery_fee,
    st.amount_incl_vat - st.discount_amount + sp.delivery_fee,
    CASE
        WHEN so.h_misc % 25 = 0 THEN 'CANCELLED'
        WHEN so.order_date >= DATE '2026-11-20' AND so.h_misc % 2 = 0 THEN 'NEW'
        WHEN so.order_date >= DATE '2026-11-20' THEN 'CONFIRMED'
        ELSE 'DONE'
    END,
    NULL,
    md5(so.h) || md5(so.h || '-request'),
    (so.order_date + sp.slot_start - INTERVAL '2 hours') AT TIME ZONE so.time_zone,
    (so.order_date + sp.slot_start - INTERVAL '2 hours') AT TIME ZONE so.time_zone
FROM seed_order so
JOIN seed_place sp ON sp.order_number = so.order_number
JOIN seed_total st ON st.order_number = so.order_number
ORDER BY so.order_date, so.code, so.order_number;

INSERT INTO order_item (order_id, product_id, product_name, quantity, unit_price, line_total)
SELECT o.id, sl.product_id, sl.product_name, sl.quantity, sl.unit_price, sl.line_total
FROM seed_line sl
JOIN customer_order o ON o.order_number = sl.order_number
ORDER BY o.id, sl.ln;

DROP TABLE seed_total;
DROP TABLE seed_line;
DROP TABLE seed_place;
DROP TABLE seed_order;
