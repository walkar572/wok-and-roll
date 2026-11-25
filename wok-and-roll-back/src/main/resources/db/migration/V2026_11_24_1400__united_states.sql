-- A seventh country: the United States (USA), US dollar, American English in the interface, with
-- two cafés in Boston. Everything other countries have, in the same way as their seeds
-- (V2026_11_24_1000 for the United Kingdom):
--   tax 7.00 % (Massachusetts sales tax on meals 6.25 % and the Boston local meals excise 0.75 %;
--   menu prices are before tax, like everywhere in the US), America/New_York time zone, prices,
--   modifier prices, discount thresholds and zone fees at the level of Tashkent converted at the
--   November 2026 rate (V2026_11_22_0900): 1 USD = 12,700 UZS, rounded to 5 cents; 2 cafés with
--   6 tables each, 3 delivery zones (near, centre, far) and a sales history from 1 Jun to
--   22 Nov 2026 for the reports. Every dish of the menu (24, with the dishes of V2026_11_24_1200)
--   is sold here; tuna nigiri is out of stock, like in every country.
-- Rows are linked by natural keys (country code, product, café and modifier names), not by ids.

-- 1. Currency, country, tax
INSERT INTO currency (code, name) VALUES ('USD', 'US dollar');

INSERT INTO country (code, name, currency_code, time_zone)
VALUES ('USA', 'United States', 'USD', 'America/New_York');

INSERT INTO vat_rate (country_id, rate)
SELECT c.id, 7.00 FROM country c WHERE c.code = 'USA';

-- 2. Discount thresholds (amount incl. tax): 100,000 / 500,000 / 700,000 / 1,000,000 / 5,000,000 UZS
--    are about 8 / 39 / 55 / 79 / 394 USD.
INSERT INTO discount_threshold (country_id, min_amount, discount_rate)
SELECT c.id, t.min_amount, t.discount_rate
FROM country c
CROSS JOIN (VALUES
    (8.00,    3.00),
    (40.00,   5.00),
    (55.00,   7.00),
    (80.00,  10.00),
    (400.00, 15.00)
) AS t (min_amount, discount_rate)
WHERE c.code = 'USA'
ORDER BY t.min_amount;

-- 3. Dishes, before tax. Edamame is hidden from the menu (V2026_11_24_1200) but keeps a price
--    like in every country, so an admin can show it again.
INSERT INTO regional_price (product_id, country_id, price, in_stock)
SELECT p.id, c.id, x.price, x.in_stock
FROM (VALUES
    -- product                      USD    in_stock   (UZS)
    ('Salmon avocado roll',        6.20,  TRUE),    -- 79,000
    ('Tuna nigiri',                2.70,  FALSE),   -- 34,000
    ('California roll',            5.45,  TRUE),    -- 69,000
    ('Wok & Roll set for two',    14.90,  TRUE),    -- 189,000
    ('Firecracker udon',           4.35,  TRUE),    -- 55,000
    ('Miso mushroom rice',         3.55,  TRUE),    -- 45,000
    ('Teriyaki chicken noodles',   4.10,  TRUE),    -- 52,000
    ('Gyoza',                      3.05,  TRUE),    -- 39,000
    ('Edamame',                    2.05,  TRUE),    -- 26,000
    ('Yuzu lemonade',              1.90,  TRUE),    -- 24,000
    ('Philadelphia roll',          6.70,  TRUE),    -- 85,000
    ('Dragon roll',                7.50,  TRUE),    -- 95,000
    ('Crunchy shrimp roll',        6.45,  TRUE),    -- 82,000
    ('Chicken ramen',              5.10,  TRUE),    -- 65,000
    ('Beef yakisoba',              4.90,  TRUE),    -- 62,000
    ('Spicy egg fried rice',       3.80,  TRUE),    -- 48,000
    ('Chicken karaage',            3.30,  TRUE),    -- 42,000
    ('Vegetable spring rolls',     2.50,  TRUE),    -- 32,000
    ('Chili garlic peanuts',       1.50,  TRUE),    -- 19,000
    ('Mochi ice cream',            2.85,  TRUE),    -- 36,000
    ('Matcha cheesecake',          3.30,  TRUE),    -- 42,000
    ('Matcha latte',               2.35,  TRUE),    -- 30,000
    ('Jasmine green tea',          1.40,  TRUE),    -- 18,000
    ('Salmon sashimi',             5.45,  TRUE),    -- 69,000
    ('Mango sticky rice',          3.00,  TRUE)     -- 38,000
) AS x (product_name, price, in_stock)
JOIN product p ON p.name = x.product_name
JOIN country c ON c.code = 'USA'
ORDER BY p.id;

-- 4. Modifier prices: every modifier is sold in the US.
INSERT INTO modifier_price (modifier_id, country_id, price_delta)
SELECT md.id, c.id, x.price_delta
FROM (VALUES
    -- product               modifier          USD    (UZS)
    ('Firecracker udon',   'Regular',        0.00),
    ('Firecracker udon',   'Large',          1.20),  -- 15,000
    ('Firecracker udon',   'Extra chicken',  0.95),  -- 12,000
    ('Firecracker udon',   'Fried egg',      0.45),  -- 6,000
    ('Miso mushroom rice', 'Regular',        0.00),
    ('Miso mushroom rice', 'Large',          0.95),  -- 12,000
    ('Miso mushroom rice', 'Extra tofu',     0.70),  -- 9,000
    ('Yuzu lemonade',      '400 ml',         0.00),
    ('Yuzu lemonade',      '600 ml',         0.65),  -- 8,000
    ('Chicken ramen',      'Soft egg',       0.55),  -- 7,000
    ('Matcha latte',       'Oat milk',       0.40)   -- 5,000
) AS x (product_name, modifier_name, price_delta)
JOIN product p ON p.name = x.product_name
JOIN modifier md ON md.product_id = p.id AND md.name = x.modifier_name
JOIN country c ON c.code = 'USA'
ORDER BY md.id;

-- 5. Cafés in Boston (invented; phones from the 555-0100 to 0199 range kept for fiction), open
--    10:00-22:00, 5 orders per slot, 6 tables each like the other cafés. The places on the map
--    are the street addresses, found with OpenStreetMap Nominatim like the others.
INSERT INTO cafe (country_id, name, city, address, phone, opens_at, closes_at, slot_capacity,
                  latitude, longitude)
SELECT c.id, v.name, 'Boston', v.address, v.phone, TIME '10:00', TIME '22:00', 5,
       v.latitude, v.longitude
FROM (VALUES
    (1, 'Wok & Roll Back Bay', '230 Newbury St, Boston',  '+16175550142', 42.349702, -71.081401),
    (2, 'Wok & Roll Seaport',  '60 Seaport Blvd, Boston', '+16175550173', 42.352933, -71.046981)
) AS v (n, name, address, phone, latitude, longitude)
JOIN country c ON c.code = 'USA'
ORDER BY v.n;

INSERT INTO cafe_table (cafe_id, label, seats)
SELECT cf.id, t.label, t.seats
FROM cafe cf
JOIN country c ON c.id = cf.country_id AND c.code = 'USA'
CROSS JOIN (VALUES
    ('T1', 2),
    ('T2', 2),
    ('T3', 4),
    ('T4', 4),
    ('T5', 6),
    ('T6', 8)
) AS t (label, seats)
ORDER BY cf.id, t.label;

-- 6. Delivery zones: near (the district of a café), centre and far, like the other countries.
--    15,000 / 20,000 / 30,000 UZS are about 1.20 / 1.55 / 2.35 USD.
INSERT INTO delivery_zone (country_id, cafe_id, name, fee, delivery_minutes)
SELECT c.id, cf.id, v.name, v.fee, v.delivery_minutes
FROM (VALUES
    (1, 'Wok & Roll Back Bay', 'Boston: Back Bay',   1.20, 40),
    (2, 'Wok & Roll Seaport',  'Boston: Downtown',   1.55, 45),
    (3, 'Wok & Roll Seaport',  'Boston: Dorchester', 2.35, 60)
) AS v (n, cafe_name, name, fee, delivery_minutes)
JOIN country c ON c.code = 'USA'
JOIN cafe cf ON cf.country_id = c.id AND cf.name = v.cafe_name
ORDER BY v.n;

-- 7. Sales history for the reports: the same rules as V2026_11_23_0900 (deterministic md5 keys,
--    every order priced with the formula of api-contract.md section 5), only for USA.

CREATE TEMP TABLE seed_order AS
WITH day_country AS (
    SELECT d::date AS day, c.id AS country_id, c.code, c.currency_code, c.time_zone,
           vr.rate AS vat_rate
    FROM generate_series(DATE '2026-06-01', DATE '2026-11-22', INTERVAL '1 day') AS d
    CROSS JOIN country c
    JOIN vat_rate vr ON vr.country_id = c.id
    WHERE c.code = 'USA'
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
        + 1
    ) AS n
),
keyed AS (
    SELECT nb.*, md5('wr-seed-order-' || nb.code || '-' || nb.day || '-' || nb.n) AS h
    FROM numbered nb
)
SELECT
    'WR-' || upper(substr(k.h, 1, 8)) AS order_number,
    k.country_id, k.code, k.currency_code, k.time_zone, k.vat_rate, k.day AS order_date, k.h,
    ('x' || substr(k.h, 9, 7))::bit(28)::int  AS h_scenario,
    ('x' || substr(k.h, 16, 7))::bit(28)::int AS h_place,
    ('x' || substr(k.h, 23, 7))::bit(28)::int AS h_misc
FROM keyed k;

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
         THEN (1 + so.h_place % 120) || ' Demo Street, flat ' || (1 + so.h_misc % 80) END,
    (ARRAY['Liam Johnson', 'Emma Williams', 'Ethan Miller', 'Ava Garcia', 'Mason Davis',
           'Mia Rodriguez', 'Lucas Martinez', 'Harper Anderson', 'Logan Thomas',
           'Ella Jackson'])[1 + so.h_misc % 10],
    -- 617-555-0100 to 0199: the range kept for fiction, never a real number
    '+16175550' || lpad((100 + so.h_place % 100)::text, 3, '0'),
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
ORDER BY so.order_date, so.order_number;

INSERT INTO order_item (order_id, product_id, product_name, quantity, unit_price, line_total)
SELECT o.id, sl.product_id, sl.product_name, sl.quantity, sl.unit_price, sl.line_total
FROM seed_line sl
JOIN customer_order o ON o.order_number = sl.order_number
ORDER BY o.id, sl.ln;

DROP TABLE seed_total;
DROP TABLE seed_line;
DROP TABLE seed_place;
DROP TABLE seed_order;
