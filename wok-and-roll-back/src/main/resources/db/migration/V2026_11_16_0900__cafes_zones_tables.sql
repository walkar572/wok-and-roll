-- Refs #143. Cafés, delivery zones, tables, slot places and country time zones, with seed data.
-- Source: wok-and-roll-back/docs/erd.md v1.1, part "Cafés, delivery zones, time slots, booking",
-- and docs/api-contract.md v1.1 §1, §3.1, §3.6–3.8. The booking table comes with the booking task.
-- Rows are linked by natural keys (country code, café name), not by ids.
-- Times are local times of the café's country. Money is numeric(12,2) in the local currency.

-- 1. Time zone of each country (IANA name). CountryClock reads it for "today" and "now".
ALTER TABLE country ADD COLUMN time_zone VARCHAR(50);

UPDATE country c
SET time_zone = tz.time_zone
FROM (VALUES
    ('UZB', 'Asia/Tashkent'),
    ('KAZ', 'Asia/Almaty'),
    ('GEO', 'Asia/Tbilisi'),
    ('UKR', 'Europe/Kyiv'),
    ('CHN', 'Asia/Shanghai')
) AS tz (country_code, time_zone)
WHERE c.code = tz.country_code;

ALTER TABLE country ALTER COLUMN time_zone SET NOT NULL;

-- 2. Cafés: pickup point, booking venue and kitchen for its delivery zones (contract §3.6).
CREATE TABLE cafe (
    id             BIGSERIAL    PRIMARY KEY,
    country_id     BIGINT       NOT NULL REFERENCES country (id),
    name           VARCHAR(100) NOT NULL,
    address        VARCHAR(255) NOT NULL,
    phone          VARCHAR(30),
    opens_at       TIME         NOT NULL,                        -- local time
    closes_at      TIME         NOT NULL,                        -- local time
    slot_capacity  INT          NOT NULL,                        -- orders per 30-minute slot
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cafe_name UNIQUE (country_id, name),
    CONSTRAINT uq_cafe_country UNIQUE (id, country_id),          -- target of the zone foreign key
    CONSTRAINT ck_cafe_hours CHECK (closes_at - opens_at >= INTERVAL '30 minutes'),
    CONSTRAINT ck_cafe_slot_capacity CHECK (slot_capacity >= 1)
);

-- 3. Delivery zones with a fixed fee (contract §3.7). The café that cooks for a zone must be in
--    the same country: the foreign key is on (cafe_id, country_id).
CREATE TABLE delivery_zone (
    id                BIGSERIAL      PRIMARY KEY,
    country_id        BIGINT         NOT NULL REFERENCES country (id),
    cafe_id           BIGINT         NOT NULL,
    name              VARCHAR(100)   NOT NULL,
    fee               NUMERIC(12, 2) NOT NULL,                   -- local currency, outside VAT
    delivery_minutes  INT            NOT NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_delivery_zone_cafe FOREIGN KEY (cafe_id, country_id)
        REFERENCES cafe (id, country_id),
    CONSTRAINT uq_delivery_zone_name UNIQUE (country_id, name),
    CONSTRAINT ck_delivery_zone_fee CHECK (fee >= 0),
    CONSTRAINT ck_delivery_zone_minutes CHECK (delivery_minutes > 0)
);
CREATE INDEX ix_delivery_zone_cafe ON delivery_zone (cafe_id);

-- 4. Tables of a café for booking (contract §3.10).
CREATE TABLE cafe_table (
    id       BIGSERIAL   PRIMARY KEY,
    cafe_id  BIGINT      NOT NULL REFERENCES cafe (id),
    label    VARCHAR(20) NOT NULL,
    seats    INT         NOT NULL,
    CONSTRAINT uq_cafe_table_label UNIQUE (cafe_id, label),
    CONSTRAINT ck_cafe_table_seats CHECK (seats IN (2, 4, 6, 8))
);

-- 5. Taken places per café and 30-minute slot. Only taken places are stored; the slot list is
--    calculated from the opening hours. The primary key is the ON CONFLICT target of the atomic
--    reservation in contract §3.8, which also keeps reserved <= cafe.slot_capacity.
CREATE TABLE slot_reservation (
    cafe_id     BIGINT NOT NULL REFERENCES cafe (id),
    slot_date   DATE   NOT NULL,                                 -- local date
    slot_start  TIME   NOT NULL,                                 -- local time
    reserved    INT    NOT NULL,
    PRIMARY KEY (cafe_id, slot_date, slot_start),
    CONSTRAINT ck_slot_reservation_reserved CHECK (reserved >= 0)
);

-- 6. Seed: 2 cafés per country, open 10:00–22:00, 5 orders per slot. Invented addresses and
--    phones. ORDER BY gives the ids in the order of this list (1 = Wok & Roll Chilanzar).
INSERT INTO cafe (country_id, name, address, phone, opens_at, closes_at, slot_capacity)
SELECT c.id, v.name, v.address, v.phone, TIME '10:00', TIME '22:00', 5
FROM (VALUES
    (1,  'UZB', 'Wok & Roll Chilanzar',  '12 Bunyodkor Ave, Tashkent',    '+998712000001'),
    (2,  'UZB', 'Wok & Roll Yunusabad',  '7 Amir Temur Ave, Tashkent',    '+998712000002'),
    (3,  'KAZ', 'Wok & Roll Medeu',      '45 Dostyk Ave, Almaty',         '+77272000001'),
    (4,  'KAZ', 'Wok & Roll Bostandyk',  '120 Abay Ave, Almaty',          '+77272000002'),
    (5,  'GEO', 'Wok & Roll Vake',       '30 Chavchavadze Ave, Tbilisi',  '+995322000001'),
    (6,  'GEO', 'Wok & Roll Saburtalo',  '15 Pekini Ave, Tbilisi',        '+995322000002'),
    (7,  'UKR', 'Wok & Roll Podil',      '10 Sahaidachnoho St, Kyiv',     '+380442000001'),
    (8,  'UKR', 'Wok & Roll Pechersk',   '25 Lesi Ukrainky Blvd, Kyiv',   '+380442000002'),
    (9,  'CHN', 'Wok & Roll Jingan',     '88 West Nanjing Rd, Shanghai',  '+862120000001'),
    (10, 'CHN', 'Wok & Roll Pudong',     '200 Century Ave, Shanghai',     '+862120000002')
) AS v (n, country_code, name, address, phone)
JOIN country c ON c.code = v.country_code
ORDER BY v.n;

-- 7. Seed: 3 zones per country with fees 15.00 / 20.00 / 30.00 in local currency. The 20.00 zone
--    is there for the golden delivery examples (pricing-examples.json). Each zone has one café.
INSERT INTO delivery_zone (country_id, cafe_id, name, fee, delivery_minutes)
SELECT c.id, cf.id, v.name, v.fee, v.delivery_minutes
FROM (VALUES
    (1,  'UZB', 'Wok & Roll Chilanzar', 'Tashkent: Center',    20.00, 45),
    (2,  'UZB', 'Wok & Roll Yunusabad', 'Tashkent: Yunusabad', 15.00, 40),
    (3,  'UZB', 'Wok & Roll Chilanzar', 'Tashkent: Sergeli',   30.00, 60),
    (4,  'KAZ', 'Wok & Roll Medeu',     'Almaty: Center',      20.00, 45),
    (5,  'KAZ', 'Wok & Roll Bostandyk', 'Almaty: Bostandyk',   15.00, 40),
    (6,  'KAZ', 'Wok & Roll Medeu',     'Almaty: Alatau',      30.00, 60),
    (7,  'GEO', 'Wok & Roll Vake',      'Tbilisi: Center',     20.00, 45),
    (8,  'GEO', 'Wok & Roll Saburtalo', 'Tbilisi: Saburtalo',  15.00, 40),
    (9,  'GEO', 'Wok & Roll Vake',      'Tbilisi: Gldani',     30.00, 60),
    (10, 'UKR', 'Wok & Roll Podil',     'Kyiv: Center',        20.00, 45),
    (11, 'UKR', 'Wok & Roll Pechersk',  'Kyiv: Pechersk',      15.00, 40),
    (12, 'UKR', 'Wok & Roll Podil',     'Kyiv: Obolon',        30.00, 60),
    (13, 'CHN', 'Wok & Roll Jingan',    'Shanghai: Center',    20.00, 45),
    (14, 'CHN', 'Wok & Roll Pudong',    'Shanghai: Pudong',    15.00, 40),
    (15, 'CHN', 'Wok & Roll Jingan',    'Shanghai: Minhang',   30.00, 60)
) AS v (n, country_code, cafe_name, name, fee, delivery_minutes)
JOIN country c ON c.code = v.country_code
JOIN cafe cf ON cf.country_id = c.id AND cf.name = v.cafe_name
ORDER BY v.n;

-- 8. Seed: 6 tables per café with 2, 4, 6 and 8 seats.
INSERT INTO cafe_table (cafe_id, label, seats)
SELECT cf.id, t.label, t.seats
FROM cafe cf
CROSS JOIN (VALUES
    ('T1', 2),
    ('T2', 2),
    ('T3', 4),
    ('T4', 4),
    ('T5', 6),
    ('T6', 8)
) AS t (label, seats)
ORDER BY cf.id, t.label;
