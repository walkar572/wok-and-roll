-- Refs #33. Seed data for development and demos: 5 countries, VAT, discount thresholds, menu.
-- Runs after V1 (#32, tables) and V2026_11_06_1000 (#34, vat_rate and discount_threshold).
-- Rows are linked by natural keys (country code, category name, product name), not by ids,
-- so the ids from BIGSERIAL can be anything.
-- Rates are in percent. Money is numeric(12,2) in the local currency of each country, before VAT.
-- Seed data is the source of truth for prices; prices in Figma are examples (Decision Log, 5 Nov).

-- 1. Currencies and countries (country names are the same as in the frontend country switcher)
INSERT INTO currency (code, name) VALUES
    ('UZS', 'Uzbekistani som'),
    ('KZT', 'Kazakhstani tenge'),
    ('GEL', 'Georgian lari'),
    ('UAH', 'Ukrainian hryvnia'),
    ('CNY', 'Chinese yuan');

INSERT INTO country (code, name, currency_code) VALUES
    ('UZB', 'Uzbekistan', 'UZS'),
    ('KAZ', 'Kazakhstan', 'KZT'),
    ('GEO', 'Georgia',    'GEL'),
    ('UKR', 'Ukraine',    'UAH'),
    ('CHN', 'China',      'CNY');

-- 2. VAT, one rate per country (same rates as wok-and-roll-qa/test-data/pricing-examples.json)
INSERT INTO vat_rate (country_id, rate)
SELECT c.id, v.rate
FROM (VALUES
    ('UZB', 15.00),
    ('KAZ', 20.00),
    ('GEO', 12.00),
    ('UKR',  8.00),
    ('CHN', 18.00)
) AS v (country_code, rate)
JOIN country c ON c.code = v.country_code
ORDER BY c.id;

-- 3. Discount thresholds: the same 5 tiers for every country, in its local currency, no conversion
--    (Decision Log, 5 Nov). Below 100 there is no row, so no discount.
INSERT INTO discount_threshold (country_id, min_amount, discount_rate)
SELECT c.id, t.min_amount, t.discount_rate
FROM country c
CROSS JOIN (VALUES
    (100.00,   3.00),
    (500.00,   5.00),
    (700.00,   7.00),
    (1000.00, 10.00),
    (5000.00, 15.00)
) AS t (min_amount, discount_rate)
ORDER BY c.id, t.min_amount;

-- 4. Categories
INSERT INTO category (name, sort_order) VALUES
    ('Sushi & rolls', 1),
    ('From the wok',  2),
    ('Sides',         3);

-- 5. Products. image_url points to wok-and-roll-front/public/images; NULL = no photo yet.
--    ORDER BY p.n gives the products their ids in the order of this list (1 = Salmon avocado roll).
INSERT INTO product (category_id, name, description, image_url)
SELECT cat.id, p.name, p.description, p.image_url
FROM (VALUES
    (1,  'Sushi & rolls', 'Salmon avocado roll',      'Salmon, avocado, cucumber. 8 pieces.',              '/images/salmon-roll.png'),
    (2,  'Sushi & rolls', 'Tuna nigiri',              'Tuna on seasoned rice. 2 pieces.',                  '/images/tuna-nigiri.png'),
    (3,  'Sushi & rolls', 'California roll',          'Crab, avocado, cucumber, sesame. 8 pieces.',        NULL),
    (4,  'Sushi & rolls', 'Wok & Roll set for two',   'Salmon avocado roll, California roll and gyoza.',   NULL),
    (5,  'From the wok',  'Firecracker udon',         'Udon noodles, chicken, chili, sesame.',             '/images/udon.png'),
    (6,  'From the wok',  'Miso mushroom rice',       'Shiitake, seasonal greens, miso.',                  '/images/mushroom-rice.png'),
    (7,  'From the wok',  'Teriyaki chicken noodles', 'Egg noodles, chicken, teriyaki sauce, vegetables.', NULL),
    (8,  'Sides',         'Gyoza',                    'Pan-fried chicken dumplings. 5 pieces.',            '/images/gyoza.png'),
    (9,  'Sides',         'Edamame',                  'Steamed soy beans with sea salt.',                  NULL),
    (10, 'Sides',         'Yuzu lemonade',            'Homemade lemonade with yuzu. 400 ml.',              '/images/yuzu-lemonade.png')
) AS p (n, category_name, name, description, image_url)
JOIN category cat ON cat.name = p.category_name
ORDER BY p.n;

-- 6. Regional prices: one row per product and country, local currency, before VAT.
--    The same 20.00-150.00 scale in every country with small differences.
--    Tuna nigiri is out of stock in every country, to test the "out of stock" state (#51).
--    Each table row is split into 5 rows (one per country) by the LATERAL VALUES list.
INSERT INTO regional_price (product_id, country_id, price, in_stock)
SELECT p.id, c.id, x.price, m.in_stock
FROM (VALUES
    -- product                      UZB     KAZ     GEO     UKR     CHN   in_stock
    ('Salmon avocado roll',       62.00,  67.00,  58.50,  54.50,  69.50,  TRUE),
    ('Tuna nigiri',               48.00,  52.00,  45.00,  42.00,  54.00,  FALSE),
    ('California roll',           55.00,  59.50,  51.50,  48.50,  61.50,  TRUE),
    ('Wok & Roll set for two',   129.00, 139.50, 121.50, 113.50, 144.50,  TRUE),
    ('Firecracker udon',          48.00,  52.00,  45.00,  42.00,  54.00,  TRUE),
    ('Miso mushroom rice',        44.00,  47.50,  41.50,  38.50,  49.50,  TRUE),
    ('Teriyaki chicken noodles',  52.00,  56.00,  49.00,  46.00,  58.00,  TRUE),
    ('Gyoza',                     32.00,  34.50,  30.00,  28.00,  36.00,  TRUE),
    ('Edamame',                   24.00,  26.00,  22.50,  21.00,  27.00,  TRUE),
    ('Yuzu lemonade',             24.00,  26.00,  22.50,  21.00,  27.00,  TRUE)
) AS m (product_name, uzb, kaz, geo, ukr, chn, in_stock)
CROSS JOIN LATERAL (VALUES
    ('UZB', m.uzb),
    ('KAZ', m.kaz),
    ('GEO', m.geo),
    ('UKR', m.ukr),
    ('CHN', m.chn)
) AS x (country_code, price)
JOIN product p ON p.name = m.product_name
JOIN country c ON c.code = x.country_code
ORDER BY p.id, c.id;
