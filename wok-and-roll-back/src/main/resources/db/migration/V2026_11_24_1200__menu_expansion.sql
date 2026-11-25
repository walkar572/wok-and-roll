-- A bigger menu (contract v1.8): 15 new dishes (24 on the menu), two new categories (Desserts, Drinks) and
-- peanuts in place of edamame. Prices are at the level of Tashkent, converted to every currency
-- like the rest of the menu (V2026_11_22_0900, §5). Every new dish is sold and in stock in all six
-- countries.

-- 1. Categories. The lemonade moves to Drinks.
INSERT INTO category (name, sort_order) VALUES ('Desserts', 4), ('Drinks', 5);

UPDATE product
SET category_id = (SELECT id FROM category WHERE name = 'Drinks'), version = version + 1
WHERE name = 'Yuzu lemonade';

-- 2. Edamame leaves the menu; past orders keep its name (order_item.product_name), so the history
--    and the reports do not change.
UPDATE product SET visible = FALSE, version = version + 1 WHERE name = 'Edamame';

-- 3. The new dishes. The description holds only the ingredients; the portion is in its own columns.
INSERT INTO product (category_id, name, description, image_url, pieces, weight_grams, volume_ml)
SELECT c.id, v.name, v.description, v.image_url, v.pieces, v.weight_grams, v.volume_ml
FROM (VALUES
    (1,  'Sushi & rolls', 'Philadelphia roll',      'Salmon, cream cheese, cucumber.',
         '/images/philadelphia-roll.png',   8,    240,  NULL),
    (2,  'Sushi & rolls', 'Dragon roll',            'Eel, avocado, cucumber, unagi sauce, sesame.',
         '/images/dragon-roll.png',         8,    260,  NULL),
    (3,  'Sushi & rolls', 'Crunchy shrimp roll',    'Shrimp tempura, cucumber, spicy mayo, crispy kataifi.',
         '/images/crunchy-shrimp-roll.png', 8,    250,  NULL),
    (4,  'From the wok',  'Chicken ramen',          'Wheat noodles, chicken broth, chicken chashu, soft egg, bamboo shoots, nori.',
         '/images/chicken-ramen.png',       NULL, 500,  NULL),
    (5,  'From the wok',  'Beef yakisoba',          'Wheat noodles, beef, peppers, cabbage, yakisoba sauce.',
         '/images/beef-yakisoba.png',       NULL, 350,  NULL),
    (6,  'From the wok',  'Spicy egg fried rice',   'Wok-fried rice, chili, kimchi, spring onion, fried egg.',
         '/images/egg-fried-rice.png',      NULL, 330,  NULL),
    (7,  'Sides',         'Chicken karaage',        'Fried chicken thigh, ginger, garlic, lemon, Japanese mayo.',
         '/images/chicken-karaage.png',     6,    180,  NULL),
    (8,  'Sides',         'Vegetable spring rolls', 'Cabbage, carrot, glass noodles, sweet chili sauce.',
         '/images/spring-rolls.png',        4,    160,  NULL),
    (9,  'Sides',         'Chili garlic peanuts',   'Wok-roasted peanuts, garlic, chili, sea salt.',
         '/images/chilli-peanuts.png',      NULL, 120,  NULL),
    (10, 'Desserts',      'Mochi ice cream',        'Mango, strawberry and coconut.',
         '/images/mochi-ice-cream.png',     3,    105,  NULL),
    (11, 'Desserts',      'Matcha cheesecake',      'Baked cheesecake with matcha, mango sorbet.',
         '/images/matcha-cheesecake.png',   NULL, 140,  NULL),
    (12, 'Drinks',        'Matcha latte',           'Matcha, milk, ice.',
         '/images/matcha-latte.png',        NULL, NULL, 350),
    (13, 'Drinks',        'Jasmine green tea',      'Jasmine green tea, a pot for one.',
         '/images/jasmine-tea.png',         NULL, NULL, 500),
    (14, 'Sushi & rolls', 'Salmon sashimi',         'Sliced fresh salmon, soy sauce, wasabi.',
         '/images/salmon-sashimi.png',      6,    120,  NULL),
    (15, 'Desserts',      'Mango sticky rice',      'Sticky rice, mango, coconut cream.',
         '/images/mango-sticky-rice.png',   NULL, 220,  NULL)
) AS v (n, category, name, description, image_url, pieces, weight_grams, volume_ml)
JOIN category c ON c.name = v.category
ORDER BY v.n;

-- 4. Prices in each currency: UZS, KZT, GEL, UAH, CNY, GBP.
INSERT INTO regional_price (product_id, country_id, price, in_stock)
SELECT p.id, co.id, v.price, TRUE
FROM (VALUES
    ('Philadelphia roll',      'UZB', 85000), ('Philadelphia roll',      'KAZ', 3390),
    ('Philadelphia roll',      'GEO', 18.50), ('Philadelphia roll',      'UKR', 279),
    ('Philadelphia roll',      'CHN', 48),    ('Philadelphia roll',      'GBR', 5.35),
    ('Dragon roll',            'UZB', 95000), ('Dragon roll',            'KAZ', 3790),
    ('Dragon roll',            'GEO', 20.50), ('Dragon roll',            'UKR', 309),
    ('Dragon roll',            'CHN', 54),    ('Dragon roll',            'GBR', 5.95),
    ('Crunchy shrimp roll',    'UZB', 82000), ('Crunchy shrimp roll',    'KAZ', 3290),
    ('Crunchy shrimp roll',    'GEO', 17.50), ('Crunchy shrimp roll',    'UKR', 269),
    ('Crunchy shrimp roll',    'CHN', 46),    ('Crunchy shrimp roll',    'GBR', 5.15),
    ('Chicken ramen',          'UZB', 65000), ('Chicken ramen',          'KAZ', 2590),
    ('Chicken ramen',          'GEO', 13.90), ('Chicken ramen',          'UKR', 215),
    ('Chicken ramen',          'CHN', 37),    ('Chicken ramen',          'GBR', 4.05),
    ('Beef yakisoba',          'UZB', 62000), ('Beef yakisoba',          'KAZ', 2490),
    ('Beef yakisoba',          'GEO', 13.50), ('Beef yakisoba',          'UKR', 205),
    ('Beef yakisoba',          'CHN', 35),    ('Beef yakisoba',          'GBR', 3.90),
    ('Spicy egg fried rice',   'UZB', 48000), ('Spicy egg fried rice',   'KAZ', 1890),
    ('Spicy egg fried rice',   'GEO', 10.50), ('Spicy egg fried rice',   'UKR', 155),
    ('Spicy egg fried rice',   'CHN', 27),    ('Spicy egg fried rice',   'GBR', 2.95),
    ('Chicken karaage',        'UZB', 42000), ('Chicken karaage',        'KAZ', 1690),
    ('Chicken karaage',        'GEO', 8.90),  ('Chicken karaage',        'UKR', 139),
    ('Chicken karaage',        'CHN', 24),    ('Chicken karaage',        'GBR', 2.65),
    ('Vegetable spring rolls', 'UZB', 32000), ('Vegetable spring rolls', 'KAZ', 1290),
    ('Vegetable spring rolls', 'GEO', 6.90),  ('Vegetable spring rolls', 'UKR', 105),
    ('Vegetable spring rolls', 'CHN', 18),    ('Vegetable spring rolls', 'GBR', 1.95),
    ('Chili garlic peanuts',   'UZB', 19000), ('Chili garlic peanuts',   'KAZ', 750),
    ('Chili garlic peanuts',   'GEO', 3.90),  ('Chili garlic peanuts',   'UKR', 59),
    ('Chili garlic peanuts',   'CHN', 11),    ('Chili garlic peanuts',   'GBR', 1.15),
    ('Mochi ice cream',        'UZB', 36000), ('Mochi ice cream',        'KAZ', 1450),
    ('Mochi ice cream',        'GEO', 7.90),  ('Mochi ice cream',        'UKR', 119),
    ('Mochi ice cream',        'CHN', 20),    ('Mochi ice cream',        'GBR', 2.25),
    ('Matcha cheesecake',      'UZB', 42000), ('Matcha cheesecake',      'KAZ', 1690),
    ('Matcha cheesecake',      'GEO', 8.90),  ('Matcha cheesecake',      'UKR', 139),
    ('Matcha cheesecake',      'CHN', 24),    ('Matcha cheesecake',      'GBR', 2.65),
    ('Matcha latte',           'UZB', 30000), ('Matcha latte',           'KAZ', 1190),
    ('Matcha latte',           'GEO', 6.50),  ('Matcha latte',           'UKR', 99),
    ('Matcha latte',           'CHN', 17),    ('Matcha latte',           'GBR', 1.85),
    ('Jasmine green tea',      'UZB', 18000), ('Jasmine green tea',      'KAZ', 690),
    ('Jasmine green tea',      'GEO', 3.90),  ('Jasmine green tea',      'UKR', 59),
    ('Jasmine green tea',      'CHN', 10),    ('Jasmine green tea',      'GBR', 1.15),
    ('Salmon sashimi',         'UZB', 69000), ('Salmon sashimi',         'KAZ', 2790),
    ('Salmon sashimi',         'GEO', 14.90), ('Salmon sashimi',         'UKR', 229),
    ('Salmon sashimi',         'CHN', 39),    ('Salmon sashimi',         'GBR', 4.35),
    ('Mango sticky rice',      'UZB', 38000), ('Mango sticky rice',      'KAZ', 1490),
    ('Mango sticky rice',      'GEO', 8.50),  ('Mango sticky rice',      'UKR', 125),
    ('Mango sticky rice',      'CHN', 22),    ('Mango sticky rice',      'GBR', 2.35)
) AS v (product, country, price)
JOIN product p ON p.name = v.product
JOIN country co ON co.code = v.country;

-- 5. Two extras: a soft egg for the ramen and oat milk for the latte.
INSERT INTO modifier (product_id, type, name)
SELECT p.id, 'EXTRA', v.name
FROM (VALUES ('Chicken ramen', 'Soft egg'), ('Matcha latte', 'Oat milk')) AS v (product, name)
JOIN product p ON p.name = v.product;

INSERT INTO modifier_price (modifier_id, country_id, price_delta)
SELECT m.id, co.id, v.delta
FROM (VALUES
    ('Soft egg', 'UZB', 7000), ('Soft egg', 'KAZ', 290), ('Soft egg', 'GEO', 1.50),
    ('Soft egg', 'UKR', 25),   ('Soft egg', 'CHN', 4),   ('Soft egg', 'GBR', 0.45),
    ('Oat milk', 'UZB', 5000), ('Oat milk', 'KAZ', 190), ('Oat milk', 'GEO', 1.10),
    ('Oat milk', 'UKR', 15),   ('Oat milk', 'CHN', 3),   ('Oat milk', 'GBR', 0.30)
) AS v (modifier, country, delta)
JOIN modifier m ON m.name = v.modifier
JOIN country co ON co.code = v.country;
