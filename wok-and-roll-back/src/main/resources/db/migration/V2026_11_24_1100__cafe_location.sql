-- The place of every café on the map (contract v1.7, §3.6): the home page shows the cafés of the
-- country on a map. WGS 84 degrees, 6 decimals (about 10 cm). Both are set or both are empty: a
-- new café without a place is still offered for pickup and booking, only not shown on the map.
-- The points are the street addresses of the seed, found with OpenStreetMap Nominatim.

ALTER TABLE cafe
    ADD COLUMN latitude  NUMERIC(8, 6),
    ADD COLUMN longitude NUMERIC(9, 6),
    -- Not "(both NULL) OR (both in range)": with one NULL that is NULL, and a CHECK passes on NULL.
    ADD CONSTRAINT ck_cafe_location CHECK (
        (latitude IS NULL) = (longitude IS NULL)
        AND latitude BETWEEN -90 AND 90
        AND longitude BETWEEN -180 AND 180
    );

UPDATE cafe cf
SET latitude = v.latitude, longitude = v.longitude
FROM (VALUES
    ('UZB', 'Wok & Roll Chilanzar',  41.272187,  69.203167),
    ('UZB', 'Wok & Roll Yunusabad',  41.364806,  69.287215),
    ('KAZ', 'Wok & Roll Medeu',      43.249177,  76.956143),
    ('KAZ', 'Wok & Roll Bostandyk',  43.235697,  76.874601),
    ('GEO', 'Wok & Roll Vake',       41.708788,  44.771715),
    ('GEO', 'Wok & Roll Saburtalo',  41.722491,  44.774153),
    ('UKR', 'Wok & Roll Podil',      50.460627,  30.522728),
    ('UKR', 'Wok & Roll Pechersk',   50.423492,  30.545363),
    ('CHN', 'Wok & Roll Jingan',     31.236408, 121.468466),
    ('CHN', 'Wok & Roll Pudong',     31.236161, 121.508676),
    ('GBR', 'Wok & Roll Soho',       51.514988,  -0.131504),
    ('GBR', 'Wok & Roll Shoreditch', 51.523847,  -0.080528)
) AS v (country, name, latitude, longitude)
JOIN country c ON c.code = v.country
WHERE cf.country_id = c.id AND cf.name = v.name;
