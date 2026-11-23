-- Refs #113. Seed data: SIZE and EXTRA modifiers and their price per country.
-- Runs after the catalog seed V2026_11_06_1200 (#33). Tables modifier and modifier_price are in V1 (#32).
-- Rows are linked by natural keys (product name, country code), not by ids.
-- ORDER BY m.n gives the modifiers their ids in the order of this list, so Firecracker udon
-- has ids 1-4, the same as the examples in docs/api-contract.md sections 3.4 and 3.5.
-- price_delta is in the local currency of each country, before VAT, 0.00 or more.
-- Dishes without modifiers stay as they are (hasModifiers = false), for example
-- Teriyaki chicken noodles and Gyoza.

-- 1. Modifiers. SIZE: the guest picks exactly one. EXTRA: any number, each once.
INSERT INTO modifier (product_id, type, name)
SELECT p.id, m.type, m.name
FROM (VALUES
(1, 'Firecracker udon', 'SIZE', 'Regular'),
(2, 'Firecracker udon', 'SIZE', 'Large'),
(3, 'Firecracker udon', 'EXTRA', 'Extra chicken'),
(4, 'Firecracker udon', 'EXTRA', 'Fried egg'),
(5, 'Miso mushroom rice', 'SIZE', 'Regular'),
(6, 'Miso mushroom rice', 'SIZE', 'Large'),
(7, 'Miso mushroom rice', 'EXTRA', 'Extra tofu'),
(8, 'Yuzu lemonade', 'SIZE', '400 ml'),
(9, 'Yuzu lemonade', 'SIZE', '600 ml')
) AS m (n, product_name, type, name)
JOIN product p ON p.name = m.product_name
ORDER BY m.n;

-- 2. Prices per country. NULL = the modifier is not sold in that country, so no row is inserted.
-- Extra tofu is not sold in China on purpose: QA can check that a modifier without a price
-- in the chosen country is not returned (api-contract.md 3.4).
INSERT INTO modifier_price (modifier_id, country_id, price_delta)
SELECT md.id, c.id, x.price_delta
FROM (VALUES
-- product                 modifier         UZB    KAZ    GEO    UKR    CHN
('Firecracker udon', 'Regular', 0.00, 0.00, 0.00, 0.00, 0.00),
('Firecracker udon', 'Large', 12.00, 13.00, 11.00, 10.50, 13.50),
('Firecracker udon', 'Extra chicken', 9.00, 10.00, 8.50, 8.00, 10.00),
('Firecracker udon', 'Fried egg', 5.00, 5.50, 4.50, 4.00, 5.50),
('Miso mushroom rice', 'Regular', 0.00, 0.00, 0.00, 0.00, 0.00),
('Miso mushroom rice', 'Large', 10.00, 11.00, 9.50, 9.00, 11.50),
('Miso mushroom rice', 'Extra tofu', 7.00, 7.50, 6.50, 6.00, NULL),
('Yuzu lemonade', '400 ml', 0.00, 0.00, 0.00, 0.00, 0.00),
('Yuzu lemonade', '600 ml', 8.00, 8.50, 7.50, 7.00, 9.00)
) AS mp (product_name, modifier_name, uzb, kaz, geo, ukr, chn)
CROSS JOIN LATERAL (VALUES
('UZB', mp.uzb),
('KAZ', mp.kaz),
('GEO', mp.geo),
('UKR', mp.ukr),
('CHN', mp.chn)
) AS x (country_code, price_delta)
JOIN product p ON p.name = mp.product_name
JOIN modifier md ON md.product_id = p.id AND md.name = mp.modifier_name
JOIN country c ON c.code = x.country_code
WHERE x.price_delta IS NOT NULL
ORDER BY md.id, c.id;
