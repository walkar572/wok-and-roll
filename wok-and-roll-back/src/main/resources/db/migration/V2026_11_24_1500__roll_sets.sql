-- Roll sets (contract v1.10): a set is a dish made of other dishes and costs less than they do
-- one by one. The menu shows the sum of its dishes next to its price, so the saving is visible.
-- 6 new sets in a new category "Sets" (after "Sushi & rolls"), and the set for two moves there.
-- Set prices are 15-19 % under the sum of their dishes in every currency (a test checks it).

-- 1. Which dishes make a set, and how many of each.
CREATE TABLE product_set_item (
    set_id      BIGINT   NOT NULL REFERENCES product (id),
    product_id  BIGINT   NOT NULL REFERENCES product (id),
    quantity    INT      NOT NULL,
    sort_order  INT      NOT NULL,
    CONSTRAINT pk_product_set_item PRIMARY KEY (set_id, product_id),
    CONSTRAINT uq_product_set_item_order UNIQUE (set_id, sort_order),
    CONSTRAINT ck_product_set_item_quantity CHECK (quantity BETWEEN 1 AND 20),
    CONSTRAINT ck_product_set_item_other_dish CHECK (set_id <> product_id)
);
CREATE INDEX ix_product_set_item_product ON product_set_item (product_id);

COMMENT ON TABLE product_set_item IS
    'Dishes of a set (a product made of other products). The regular price of a set in a country is the sum of quantity x regional price of its dishes.';

-- 2. The category, right after "Sushi & rolls".
UPDATE category SET sort_order = sort_order + 1 WHERE sort_order >= 2;
INSERT INTO category (name, sort_order) VALUES ('Sets', 2);

-- 3. The set for two moves to Sets. Two pots of jasmine tea join it, so its price (unchanged)
--    is under the sum of its dishes: 189,000 against 223,000 UZS.
UPDATE product
SET category_id = (SELECT id FROM category WHERE name = 'Sets'),
    description = 'Salmon avocado roll, California roll, gyoza and two pots of jasmine green tea.',
    version = version + 1
WHERE name = 'Wok & Roll set for two';

-- 4. The new sets. The portion is the sum of the dishes (a drink has no weight here).
INSERT INTO product (category_id, name, description, image_url, pieces, weight_grams, volume_ml)
SELECT c.id, v.name, v.description, v.image_url, v.pieces, v.weight_grams, NULL
FROM (VALUES
    (1, 'Philadelphia set', 'Two Philadelphia rolls and a California roll.',
        '/images/set-philadelphia.png', 24, 710),
    (2, 'Salmon set',       'Salmon sashimi, Philadelphia roll and salmon avocado roll.',
        '/images/set-salmon.png',       22, 610),
    (3, 'Dragon set',       'Dragon roll, crunchy shrimp roll and California roll.',
        '/images/set-dragon.png',       24, 740),
    (4, 'Classic set',      'Salmon avocado roll, California roll and crunchy shrimp roll.',
        '/images/set-classic.png',      24, 730),
    (5, 'Party set',        'Two Philadelphia, two California and two salmon avocado rolls, a dragon roll and a crunchy shrimp roll. For 4 to 5 guests.',
        '/images/set-party.png',        64, 1950),
    (6, 'Lunch combo',      'California roll, gyoza and a pot of jasmine green tea.',
        '/images/lunch-combo.png',      13, 380)
) AS v (n, name, description, image_url, pieces, weight_grams)
JOIN category c ON c.name = 'Sets'
ORDER BY v.n;

-- 5. What is in each set.
INSERT INTO product_set_item (set_id, product_id, quantity, sort_order)
SELECT s.id, d.id, v.quantity, v.sort_order
FROM (VALUES
    ('Wok & Roll set for two', 'Salmon avocado roll', 1, 1),
    ('Wok & Roll set for two', 'California roll',     1, 2),
    ('Wok & Roll set for two', 'Gyoza',               1, 3),
    ('Wok & Roll set for two', 'Jasmine green tea',   2, 4),
    ('Philadelphia set',       'Philadelphia roll',   2, 1),
    ('Philadelphia set',       'California roll',     1, 2),
    ('Salmon set',             'Salmon sashimi',      1, 1),
    ('Salmon set',             'Philadelphia roll',   1, 2),
    ('Salmon set',             'Salmon avocado roll', 1, 3),
    ('Dragon set',             'Dragon roll',         1, 1),
    ('Dragon set',             'Crunchy shrimp roll', 1, 2),
    ('Dragon set',             'California roll',     1, 3),
    ('Classic set',            'Salmon avocado roll', 1, 1),
    ('Classic set',            'California roll',     1, 2),
    ('Classic set',            'Crunchy shrimp roll', 1, 3),
    ('Party set',              'Philadelphia roll',   2, 1),
    ('Party set',              'California roll',     2, 2),
    ('Party set',              'Salmon avocado roll', 2, 3),
    ('Party set',              'Dragon roll',         1, 4),
    ('Party set',              'Crunchy shrimp roll', 1, 5),
    ('Lunch combo',            'California roll',     1, 1),
    ('Lunch combo',            'Gyoza',               1, 2),
    ('Lunch combo',            'Jasmine green tea',   1, 3)
) AS v (set_name, dish_name, quantity, sort_order)
JOIN product s ON s.name = v.set_name
JOIN product d ON d.name = v.dish_name;

-- 6. Prices of the new sets, before VAT, in stock in all seven countries.
--    Sum of the dishes in UZS -> set: 239,000 -> 199,000; 233,000 -> 195,000; 246,000 -> 205,000;
--    230,000 -> 192,000; 643,000 -> 519,000; 126,000 -> 105,000.
INSERT INTO regional_price (product_id, country_id, price, in_stock)
SELECT p.id, co.id, v.price, TRUE
FROM (VALUES
    ('Philadelphia set', 'UZB', 199000), ('Philadelphia set', 'KAZ', 7990),
    ('Philadelphia set', 'GEO', 42.90),  ('Philadelphia set', 'UKR', 659),
    ('Philadelphia set', 'CHN', 112),    ('Philadelphia set', 'GBR', 12.45),
    ('Philadelphia set', 'USA', 15.75),
    ('Salmon set',       'UZB', 195000), ('Salmon set',       'KAZ', 7890),
    ('Salmon set',       'GEO', 41.90),  ('Salmon set',       'UKR', 649),
    ('Salmon set',       'CHN', 110),    ('Salmon set',       'GBR', 12.25),
    ('Salmon set',       'USA', 15.35),
    ('Dragon set',       'UZB', 205000), ('Dragon set',       'KAZ', 8290),
    ('Dragon set',       'GEO', 43.90),  ('Dragon set',       'UKR', 679),
    ('Dragon set',       'CHN', 116),    ('Dragon set',       'GBR', 12.85),
    ('Dragon set',       'USA', 16.15),
    ('Classic set',      'UZB', 192000), ('Classic set',      'KAZ', 7790),
    ('Classic set',      'GEO', 40.90),  ('Classic set',      'UKR', 639),
    ('Classic set',      'CHN', 109),    ('Classic set',      'GBR', 11.95),
    ('Classic set',      'USA', 15.05),
    ('Party set',        'UZB', 519000), ('Party set',        'KAZ', 20890),
    ('Party set',        'GEO', 111.90), ('Party set',        'UKR', 1699),
    ('Party set',        'CHN', 294),    ('Party set',        'GBR', 32.45),
    ('Party set',        'USA', 40.95),
    ('Lunch combo',      'UZB', 105000), ('Lunch combo',      'KAZ', 4290),
    ('Lunch combo',      'GEO', 22.90),  ('Lunch combo',      'UKR', 349),
    ('Lunch combo',      'CHN', 59),     ('Lunch combo',      'GBR', 6.55),
    ('Lunch combo',      'USA', 8.25)
) AS v (product, country, price)
JOIN product p ON p.name = v.product
JOIN country co ON co.code = v.country;
