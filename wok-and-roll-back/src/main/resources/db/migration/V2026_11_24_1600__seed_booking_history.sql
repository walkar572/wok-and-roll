-- Booking history for the admin panel demo (api-contract.md v1.11): 26 Oct - 23 Nov 2026, all 14
-- cafés of the 7 countries, 1 to 4 bookings a café a day.
--
-- Deterministic like the sales history (V2026_11_23_0900): every "random" choice comes from md5 of
-- a fixed text key, so every database gets the same bookings and booking numbers.
-- Valid by construction: the n-th booking of a café and day takes the n-th table of the café (at
-- most 4 of its 6 tables), so two bookings never share a table at the same time (ex_booking_table_
-- time); the guests fit the table; the time is 12:00-20:00 on the 30-minute step, so the 90 minutes
-- end before the café closes at 22:00.
-- All dates are in the past: the booking tests use today and later, so nothing they book collides.

CREATE TEMP TABLE seed_booking AS
WITH day_cafe AS (
    SELECT d::date AS day, cf.id AS cafe_id, c.code, c.time_zone
    FROM generate_series(DATE '2026-10-26', DATE '2026-11-23', INTERVAL '1 day') AS d
    CROSS JOIN cafe cf
    JOIN country c ON c.id = cf.country_id
    WHERE cf.active
),
numbered AS (
    SELECT dc.*, n
    FROM day_cafe dc
    CROSS JOIN LATERAL generate_series(
        1,
        1
        + ('x' || substr(md5('wr-booking-day-' || dc.cafe_id || '-' || dc.day), 1, 7))::bit(28)::int % 3
        + CASE WHEN extract(ISODOW FROM dc.day) >= 5 THEN 1 ELSE 0 END
    ) AS n
),
tables AS (
    SELECT t.id, t.cafe_id, t.seats,
           row_number() OVER (PARTITION BY t.cafe_id ORDER BY t.id) AS idx
    FROM cafe_table t
)
SELECT nb.day, nb.cafe_id, nb.code, nb.time_zone, nb.n, t.id AS table_id, t.seats,
       md5('wr-booking-' || nb.cafe_id || '-' || nb.day || '-' || nb.n) AS h
FROM numbered nb
JOIN tables t ON t.cafe_id = nb.cafe_id AND t.idx = nb.n;

INSERT INTO booking (
    booking_number, user_id, cafe_id, table_id, booking_date, booking_time, guests, during,
    contact_name, contact_phone, contact_email, comment, status, idempotency_key, request_hash,
    created_at)
SELECT
    -- BK- and 8 hex digits: hex is a subset of the Crockford Base32 alphabet of real numbers
    'BK-' || upper(substr(sb.h, 1, 8)),
    CASE WHEN ('x' || substr(sb.h, 9, 7))::bit(28)::int % 15 = 3
         THEN (SELECT id FROM app_user WHERE email = 'customer@wokandroll.test') END,
    sb.cafe_id,
    sb.table_id,
    sb.day,
    TIME '12:00' + (('x' || substr(sb.h, 16, 7))::bit(28)::int % 17) * INTERVAL '30 minutes',
    1 + ('x' || substr(sb.h, 23, 7))::bit(28)::int % sb.seats,
    tstzrange(
        (sb.day + TIME '12:00'
            + (('x' || substr(sb.h, 16, 7))::bit(28)::int % 17) * INTERVAL '30 minutes')
            AT TIME ZONE sb.time_zone,
        (sb.day + TIME '12:00'
            + (('x' || substr(sb.h, 16, 7))::bit(28)::int % 17) * INTERVAL '30 minutes'
            + INTERVAL '90 minutes')
            AT TIME ZONE sb.time_zone),
    (ARRAY['Aziz Karimov', 'Dina Sultanova', 'Nino Beridze', 'Olena Shevchuk', 'Li Wei',
           'Timur Rakhimov', 'Aigerim Nurlanova', 'Giorgi Kapanadze', 'Andrii Bondar',
           'Mei Chen'])[1 + ('x' || substr(sb.h, 30, 3))::bit(12)::int % 10],
    CASE sb.code
        WHEN 'UZB' THEN '+99890' || lpad((('x' || substr(sb.h, 9, 7))::bit(28)::int % 10000000)::text, 7, '0')
        WHEN 'KAZ' THEN '+7701' || lpad((('x' || substr(sb.h, 9, 7))::bit(28)::int % 10000000)::text, 7, '0')
        WHEN 'GEO' THEN '+99559' || lpad((('x' || substr(sb.h, 9, 7))::bit(28)::int % 10000000)::text, 7, '0')
        WHEN 'UKR' THEN '+38067' || lpad((('x' || substr(sb.h, 9, 7))::bit(28)::int % 10000000)::text, 7, '0')
        WHEN 'CHN' THEN '+86138' || lpad((('x' || substr(sb.h, 9, 7))::bit(28)::int % 10000000)::text, 7, '0')
        -- fiction ranges: UK 07700 900xxx, US 555-01xx
        WHEN 'GBR' THEN '+447700900' || lpad((('x' || substr(sb.h, 9, 7))::bit(28)::int % 1000)::text, 3, '0')
        ELSE '+16175550' || lpad((100 + ('x' || substr(sb.h, 9, 7))::bit(28)::int % 100)::text, 3, '0')
    END,
    NULL,
    CASE ('x' || substr(sb.h, 23, 7))::bit(28)::int % 9
        WHEN 0 THEN 'A table by the window, please'
        WHEN 1 THEN 'Birthday dinner'
        ELSE NULL
    END,
    CASE WHEN ('x' || substr(sb.h, 16, 7))::bit(28)::int % 20 = 0 THEN 'CANCELLED' ELSE 'CONFIRMED' END,
    NULL,
    md5(sb.h) || md5(sb.h || '-request'),
    -- booked 1 to 5 days before, at noon in the café's time zone
    (sb.day - (1 + ('x' || substr(sb.h, 30, 3))::bit(12)::int % 5) + TIME '12:00')
        AT TIME ZONE sb.time_zone
FROM seed_booking sb
ORDER BY sb.day, sb.cafe_id, sb.n;

DROP TABLE seed_booking;
