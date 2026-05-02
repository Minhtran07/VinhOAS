PRAGMA foreign_keys = ON;

DROP TABLE IF EXISTS auto_bid_configs;
DROP TABLE IF EXISTS bid_transactions;
DROP TABLE IF EXISTS auctions;
DROP TABLE IF EXISTS items;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    username        TEXT NOT NULL UNIQUE,
    email           TEXT NOT NULL UNIQUE,
    password        TEXT NOT NULL,
    role            TEXT NOT NULL CHECK (role IN ('BIDDER','SELLER','ADMIN')),
    account_balance REAL DEFAULT 0,
    seller_rating   REAL DEFAULT 0,
    access_level    INTEGER DEFAULT 1,
    is_active       INTEGER NOT NULL DEFAULT 1,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE items (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT NOT NULL,
    description     TEXT,
    starting_price  REAL NOT NULL,
    category        TEXT NOT NULL CHECK (category IN ('ELECTRONICS','ART','VEHICLE')),
    seller_id       INTEGER NOT NULL,
    brand           TEXT,
    warranty_months INTEGER,
    artist          TEXT,
    year_created    INTEGER,
    make            TEXT,
    model           TEXT,
    mileage         INTEGER,
    created_at      TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_items_seller   ON items(seller_id);
CREATE INDEX idx_items_category ON items(category);

CREATE TABLE auctions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    item_id          INTEGER NOT NULL,
    seller_id        INTEGER NOT NULL,
    start_time       TEXT NOT NULL,
    end_time         TEXT NOT NULL,
    status           TEXT NOT NULL DEFAULT 'OPEN'
                          CHECK (status IN ('OPEN','RUNNING','FINISHED','PAID','CANCELED')),
    current_price    REAL NOT NULL,
    winner_bidder_id INTEGER,
    version          INTEGER NOT NULL DEFAULT 0,
    created_at       TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (winner_bidder_id) REFERENCES users(id) ON DELETE SET NULL
);
CREATE INDEX idx_auctions_status   ON auctions(status);
CREATE INDEX idx_auctions_end_time ON auctions(end_time);
CREATE INDEX idx_auctions_seller   ON auctions(seller_id);

CREATE TABLE bid_transactions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id  INTEGER NOT NULL,
    bidder_id   INTEGER NOT NULL,
    bid_amount  REAL NOT NULL,
    bid_time    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_auto_bid INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX idx_bids_auction_time ON bid_transactions(auction_id, bid_time);
CREATE INDEX idx_bids_bidder       ON bid_transactions(bidder_id);

CREATE TABLE auto_bid_configs (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    bidder_id     INTEGER NOT NULL,
    auction_id    INTEGER NOT NULL,
    max_bid       REAL NOT NULL,
    increment     REAL NOT NULL,
    is_active     INTEGER NOT NULL DEFAULT 1,
    registered_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (bidder_id, auction_id),
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE
);

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
    datetime('now'),
    datetime('now', '+1 day'),
    'RUNNING',
    25000000
);
