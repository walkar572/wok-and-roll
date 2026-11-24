-- Portion of a dish as separate fields, so the menu card shows the ingredients on one line and
-- "8 pcs · 250 g" on another (like the menus of the competitors). The description keeps only the
-- ingredients. All three fields are optional: a drink has a volume, a bowl only a weight.

ALTER TABLE product
    ADD COLUMN pieces       SMALLINT,
    ADD COLUMN weight_grams SMALLINT,
    ADD COLUMN volume_ml    SMALLINT,
    ADD CONSTRAINT product_pieces_positive       CHECK (pieces > 0),
    ADD CONSTRAINT product_weight_grams_positive CHECK (weight_grams > 0),
    ADD CONSTRAINT product_volume_ml_positive    CHECK (volume_ml > 0);

UPDATE product p
SET description  = x.description,
    pieces       = x.pieces,
    weight_grams = x.weight_grams,
    volume_ml    = x.volume_ml
FROM (VALUES
    ('Salmon avocado roll',      'Salmon, avocado, cucumber.',                         8,    250,  NULL),
    ('Tuna nigiri',              'Tuna on seasoned rice.',                             2,    80,   NULL),
    ('California roll',          'Crab, avocado, cucumber, sesame.',                   8,    230,  NULL),
    ('Wok & Roll set for two',   'Salmon avocado roll, California roll and gyoza.',    21,   630,  NULL),
    ('Firecracker udon',         'Udon noodles, chicken, chili, sesame.',              NULL, 350,  NULL),
    ('Miso mushroom rice',       'Shiitake, seasonal greens, miso.',                   NULL, 320,  NULL),
    ('Teriyaki chicken noodles', 'Egg noodles, chicken, teriyaki sauce, vegetables.',  NULL, 350,  NULL),
    ('Gyoza',                    'Pan-fried chicken dumplings.',                       5,    150,  NULL),
    ('Edamame',                  'Steamed soy beans with sea salt.',                   NULL, 150,  NULL),
    ('Yuzu lemonade',            'Homemade lemonade with yuzu.',                       NULL, NULL, 400)
) AS x (product_name, description, pieces, weight_grams, volume_ml)
WHERE p.name = x.product_name;
