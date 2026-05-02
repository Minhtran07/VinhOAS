-- =====================================================================
-- Schema (idempotent — KHÔNG drop bảng cũ).
-- File này được apply mỗi lần server start qua SchemaInitializer.
-- Mọi statement đều dùng CREATE ... IF NOT EXISTS để an toàn cho dữ liệu
-- đã có. Khi cần thêm cột mới, dùng ALTER TABLE ADD COLUMN ở phần
-- "MIGRATIONS" phía dưới (cũng phải idempotent).
-- =====================================================================

CREATE TABLE IF NOT EXISTS users (
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

CREATE TABLE IF NOT EXISTS items (
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
CREATE INDEX IF NOT EXISTS idx_items_seller   ON items(seller_id);
CREATE INDEX IF NOT EXISTS idx_items_category ON items(category);

CREATE TABLE IF NOT EXISTS auctions (
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
CREATE INDEX IF NOT EXISTS idx_auctions_status   ON auctions(status);
CREATE INDEX IF NOT EXISTS idx_auctions_end_time ON auctions(end_time);
CREATE INDEX IF NOT EXISTS idx_auctions_seller   ON auctions(seller_id);

CREATE TABLE IF NOT EXISTS bid_transactions (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id  INTEGER NOT NULL,
    bidder_id   INTEGER NOT NULL,
    bid_amount  REAL NOT NULL,
    bid_time    TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_auto_bid INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id) REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_bids_auction_time ON bid_transactions(auction_id, bid_time);
CREATE INDEX IF NOT EXISTS idx_bids_bidder       ON bid_transactions(bidder_id);

CREATE TABLE IF NOT EXISTS auto_bid_configs (
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

-- =====================================================================
-- MIGRATIONS — thêm cột mới ở đây khi schema thay đổi.
-- Tất cả phải idempotent (an toàn khi chạy lần thứ N).
-- SQLite không hỗ trợ ADD COLUMN IF NOT EXISTS, nên SchemaInitializer
-- sẽ check sự tồn tại của cột trước khi thêm.
-- =====================================================================

-- Bảng seller_ratings: 1 winner đánh giá 1 seller cho 1 phiên đã PAID.
-- Constraint UNIQUE (auction_id, bidder_id) đảm bảo không double-rate.
CREATE TABLE IF NOT EXISTS seller_ratings (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    auction_id  INTEGER NOT NULL,
    seller_id   INTEGER NOT NULL,
    bidder_id   INTEGER NOT NULL,
    stars       INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment     TEXT,
    created_at  TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (auction_id, bidder_id),
    FOREIGN KEY (auction_id) REFERENCES auctions(id) ON DELETE CASCADE,
    FOREIGN KEY (seller_id)  REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (bidder_id)  REFERENCES users(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_ratings_seller ON seller_ratings(seller_id);
