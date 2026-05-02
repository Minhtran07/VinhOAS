-- =====================================================================
-- Seed data — CHỈ insert khi bảng users đang trống.
-- SchemaInitializer kiểm tra trước khi chạy file này.
-- KHÔNG drop bảng — dữ liệu user đã có giữ nguyên.
-- =====================================================================

INSERT INTO users (username, email, password, role, account_balance, seller_rating, access_level)
VALUES
    ('admin',     'admin@auction.local',  'admin123',  'ADMIN',  0,           0,    5),
    ('alice_s',   'alice@auction.local',  'password',  'SELLER', 0,           4.5,  1),
    ('bob_s',     'bob@auction.local',    'password',  'SELLER', 0,           4.2,  1),
    ('charlie_b', 'charlie@auction.local','password',  'BIDDER', 100000000,   0,    1),
    ('dan_b',     'dan@auction.local',    'password',  'BIDDER', 200000000,   0,    1),
    ('eve_b',     'eve@auction.local',    'password',  'BIDDER', 500000000,   0,    1);

INSERT INTO items (name, description, starting_price, category, seller_id, brand, warranty_months)
VALUES ('iPhone 15 Pro', 'Hang chinh hang, like new', 25000000, 'ELECTRONICS',
        (SELECT id FROM users WHERE username='alice_s'),
        'Apple', 12);

INSERT INTO items (name, description, starting_price, category, seller_id, artist, year_created)
VALUES ('Buc tranh Son dau', 'Phong canh the ky 19', 8000000, 'ART',
        (SELECT id FROM users WHERE username='alice_s'),
        'Vincent', 1885);

INSERT INTO items (name, description, starting_price, category, seller_id, make, model, mileage)
VALUES ('Toyota Camry 2018', 'Xe gia dinh giu ky', 650000000, 'VEHICLE',
        (SELECT id FROM users WHERE username='bob_s'),
        'Toyota', 'Camry', 45000);

INSERT INTO auctions (item_id, seller_id, start_time, end_time, status, current_price)
VALUES (
    (SELECT id FROM items WHERE name='iPhone 15 Pro'),
    (SELECT id FROM users WHERE username='alice_s'),
    strftime('%Y-%m-%dT%H:%M:%S', 'now', 'localtime'),
    strftime('%Y-%m-%dT%H:%M:%S', 'now', '+1 day', 'localtime'),
    'RUNNING',
    25000000
);
