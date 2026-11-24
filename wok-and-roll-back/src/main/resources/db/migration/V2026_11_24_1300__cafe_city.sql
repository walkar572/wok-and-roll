-- The city of a café (contract v1.9). The interface lets a guest choose the city apart from the
-- language: the city gives the country (prices, currency, cafés, delivery zones), the language
-- only the texts. Every country has its cafés in one city for now; the column allows more.

ALTER TABLE cafe ADD COLUMN city VARCHAR(80);

UPDATE cafe cf
SET city = v.city
FROM (VALUES
    ('UZB', 'Tashkent'),
    ('KAZ', 'Almaty'),
    ('GEO', 'Tbilisi'),
    ('UKR', 'Kyiv'),
    ('CHN', 'Shanghai'),
    ('GBR', 'London')
) AS v (code, city)
JOIN country c ON c.code = v.code
WHERE cf.country_id = c.id;

ALTER TABLE cafe ALTER COLUMN city SET NOT NULL;
ALTER TABLE cafe ADD CONSTRAINT ck_cafe_city CHECK (btrim(city) <> '');

COMMENT ON COLUMN cafe.city IS 'City of the café in English, as in the address. The guest chooses it in the interface.';
