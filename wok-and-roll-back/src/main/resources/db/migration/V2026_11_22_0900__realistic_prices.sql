-- Real price levels. The first seed (#33) used the same small numbers (20.00-150.00) in every
-- currency, so a roll cost 62 sum in Tashkent. Now every price is at the level of a Tashkent sushi
-- bar (a roll about 70,000-80,000 UZS) and converted to the other currencies, rounded to prices a
-- café would print. Discount thresholds and delivery fees are converted the same way, so the
-- discount rules keep their meaning (3 % from about two dishes, 15 % only for very big orders).
--
-- Rates used for the conversion (November 2026, approximate), 1 USD =
--   12,700 UZS · 510 KZT · 2.70 GEL · 41.5 UAH · 7.15 CNY
-- VAT rates and discount percentages do not change. Runs before the sales history seed
-- (V2026_11_23_0900), so the history is priced with these numbers.

-- 1. Dishes, before VAT.
UPDATE regional_price rp
SET price = x.price
FROM (VALUES
    -- product                      UZB        KAZ      GEO     UKR     CHN
    ('Salmon avocado roll',       79000.00,  3190.00, 16.90, 259.00,  45.00),
    ('Tuna nigiri',               34000.00,  1390.00,  7.50, 115.00,  19.00),
    ('California roll',           69000.00,  2790.00, 14.90, 229.00,  39.00),
    ('Wok & Roll set for two',   189000.00,  7590.00, 39.90, 619.00, 106.00),
    ('Firecracker udon',          55000.00,  2190.00, 11.90, 179.00,  31.00),
    ('Miso mushroom rice',        45000.00,  1790.00,  9.50, 149.00,  25.00),
    ('Teriyaki chicken noodles',  52000.00,  2090.00, 10.90, 169.00,  29.00),
    ('Gyoza',                     39000.00,  1590.00,  8.50, 129.00,  22.00),
    ('Edamame',                   26000.00,  1050.00,  5.50,  85.00,  15.00),
    ('Yuzu lemonade',             24000.00,   990.00,  4.90,  79.00,  14.00)
) AS m (product_name, uzb, kaz, geo, ukr, chn)
CROSS JOIN LATERAL (VALUES
    ('UZB', m.uzb),
    ('KAZ', m.kaz),
    ('GEO', m.geo),
    ('UKR', m.ukr),
    ('CHN', m.chn)
) AS x (country_code, price)
JOIN product p ON p.name = m.product_name
JOIN country c ON c.code = x.country_code
WHERE rp.product_id = p.id AND rp.country_id = c.id;

-- 2. Modifier price changes. "Regular" and "400 ml" stay 0.00; Extra tofu is still not sold in
--    China (no row), which the tests use for "modifier not priced in this country".
UPDATE modifier_price mp
SET price_delta = x.price_delta
FROM (VALUES
    -- product               modifier          UZB        KAZ     GEO    UKR    CHN
    ('Firecracker udon',   'Large',          15000.00, 600.00, 3.20, 49.00, 8.00),
    ('Firecracker udon',   'Extra chicken',  12000.00, 490.00, 2.50, 39.00, 7.00),
    ('Firecracker udon',   'Fried egg',       6000.00, 250.00, 1.30, 20.00, 3.00),
    ('Miso mushroom rice', 'Large',          12000.00, 490.00, 2.50, 39.00, 7.00),
    ('Miso mushroom rice', 'Extra tofu',      9000.00, 350.00, 1.90, 29.00, NULL),
    ('Yuzu lemonade',      '600 ml',          8000.00, 320.00, 1.70, 25.00, 5.00)
) AS m (product_name, modifier_name, uzb, kaz, geo, ukr, chn)
CROSS JOIN LATERAL (VALUES
    ('UZB', m.uzb),
    ('KAZ', m.kaz),
    ('GEO', m.geo),
    ('UKR', m.ukr),
    ('CHN', m.chn)
) AS x (country_code, price_delta)
JOIN product p ON p.name = m.product_name
JOIN modifier md ON md.product_id = p.id AND md.name = m.modifier_name
JOIN country c ON c.code = x.country_code
WHERE mp.modifier_id = md.id AND mp.country_id = c.id AND x.price_delta IS NOT NULL;

-- 3. Discount thresholds (amount incl. VAT, local currency). The rates stay 3/5/7/10/15 %.
DELETE FROM discount_threshold;

INSERT INTO discount_threshold (country_id, min_amount, discount_rate)
SELECT c.id, t.min_amount, t.discount_rate
FROM (VALUES
    ('UZB',  100000.00,  3.00), ('UZB',  500000.00,  5.00), ('UZB',  700000.00,  7.00),
    ('UZB', 1000000.00, 10.00), ('UZB', 5000000.00, 15.00),
    ('KAZ',    4000.00,  3.00), ('KAZ',   20000.00,  5.00), ('KAZ',   28000.00,  7.00),
    ('KAZ',   40000.00, 10.00), ('KAZ',  200000.00, 15.00),
    ('GEO',      20.00,  3.00), ('GEO',     100.00,  5.00), ('GEO',     150.00,  7.00),
    ('GEO',     200.00, 10.00), ('GEO',    1000.00, 15.00),
    ('UKR',     300.00,  3.00), ('UKR',    1600.00,  5.00), ('UKR',    2300.00,  7.00),
    ('UKR',    3300.00, 10.00), ('UKR',   16000.00, 15.00),
    ('CHN',      55.00,  3.00), ('CHN',     280.00,  5.00), ('CHN',     400.00,  7.00),
    ('CHN',     550.00, 10.00), ('CHN',    2800.00, 15.00)
) AS t (country_code, min_amount, discount_rate)
JOIN country c ON c.code = t.country_code
ORDER BY c.id, t.min_amount;

-- 4. Delivery fees: the near zone, the center and the far zone of every country.
UPDATE delivery_zone z
SET fee = x.fee
FROM (VALUES
    ('Tashkent: Yunusabad', 15000.00), ('Tashkent: Center',  20000.00), ('Tashkent: Sergeli', 30000.00),
    ('Almaty: Bostandyk',     600.00), ('Almaty: Center',      800.00), ('Almaty: Alatau',     1200.00),
    ('Tbilisi: Saburtalo',      3.50), ('Tbilisi: Center',       4.50), ('Tbilisi: Gldani',       6.50),
    ('Kyiv: Pechersk',         50.00), ('Kyiv: Center',          65.00), ('Kyiv: Obolon',        100.00),
    ('Shanghai: Pudong',        8.00), ('Shanghai: Center',      11.00), ('Shanghai: Minhang',    17.00)
) AS x (zone_name, fee)
WHERE z.name = x.zone_name;

-- 5. Photos for the four dishes that had none (files in wok-and-roll-front/public/images).
UPDATE product p
SET image_url = x.image_url
FROM (VALUES
    ('California roll',          '/images/california-roll.png'),
    ('Wok & Roll set for two',   '/images/set-for-two.png'),
    ('Teriyaki chicken noodles', '/images/teriyaki-noodles.png'),
    ('Edamame',                  '/images/edamame.png')
) AS x (product_name, image_url)
WHERE p.name = x.product_name AND p.image_url IS NULL;
