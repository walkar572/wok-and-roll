-- Refs #151. One demo user per role, for the demo, QA and the frontend team.
-- Invented people with .test emails (api-contract.md section 8, personal data).
-- Only BCrypt hashes (cost 10) are stored. The passwords are in wok-and-roll-back/README.md,
-- section "Demo users".
-- Note: the comment in V2026_11_12_0900__auth_tables.sql still says these users come in #65.
-- That file is already applied everywhere, and Flyway checks its checksum, so it stays unchanged.

INSERT INTO app_user (email, password_hash, full_name, role_id)
SELECT v.email, v.password_hash, v.full_name, r.id
FROM (VALUES
    ('admin@wokandroll.test',    '$2b$10$Ci7o0WZikzxFQyt.0VWTxOPWKq33sUA4WAu35QPRf28S.5sqfJv7u', 'Demo Admin',    'ADMIN'),
    ('staff@wokandroll.test',    '$2b$10$PeE99NicwCprweDWQMRFr.kHBr9dGBWse3o6mukk8DMgUh1Tdd702', 'Demo Staff',    'STAFF'),
    ('viewer@wokandroll.test',   '$2b$10$FYyawOEX70pDTAJx7VujFOm0XptpkmyF3ym6b4Ml.fc/XStKjOLKu', 'Demo Viewer',   'VIEWER'),
    ('customer@wokandroll.test', '$2b$10$p6qUltX4bQFKbcWtwkS9P.2vbshYqmEEfm8A27PAAxryIrfHEuwfO', 'Demo Customer', 'CUSTOMER')
) AS v (email, password_hash, full_name, role)
JOIN role r ON r.code = v.role
ON CONFLICT (email) DO NOTHING;
