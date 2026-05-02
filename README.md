# VQTT Auction System

![CI](https://github.com/MinhGigachad666/Online-Auction-System/actions/workflows/ci.yml/badge.svg)

Hệ thống đấu giá trực tuyến cho bài tập lớn môn Lập Trình Nâng Cao.
Kiến trúc Client–Server, JavaFX cho UI, **SQLite** (file `auction.db`)
cho lưu trữ, JSON-over-Socket cho giao tiếp realtime.

> **Lưu ý:** Mặc định project dùng SQLite — không cần cài MySQL hay daemon
> nào. Lần đầu chạy server, file `auction.db` được tự tạo cùng schema và
> seed data. Nếu muốn đổi sang MySQL, chỉ cần sửa `db.url` trong
> `server/src/main/resources/application.properties` và thêm dependency
> JDBC driver tương ứng — code DAO không cần đổi.

## Tính năng

- Đăng ký / đăng nhập với 3 vai trò: Bidder, Seller, Admin
- Dashboard liệt kê các phiên đấu giá theo trạng thái
- Phòng đấu giá realtime với:
  - Bid lên server, validate phía DB (SELECT FOR UPDATE)
  - Push event tới mọi client đang xem (Observer Pattern)
  - Anti-sniping: bid trong 30s cuối → kéo dài 60s
  - Line chart giá theo thời gian
  - Đếm ngược thời gian còn lại
- Auto-bidding với PriorityQueue (maxBid + increment)
- Lifecycle scheduler tự chuyển OPEN→RUNNING→FINISHED khi đến giờ
- BCrypt cho password hashing
- Logback cho logging file + console

## Kiến trúc

```
+------------+   JSON line   +-------------+   JDBC pool   +----------+
|  JavaFX    | <===========> |   Server    | <===========> |  SQLite  |
|  Client    |   over Socket |  (Java SE)  |   (HikariCP)  | (1 file) |
+------------+               +-------------+               +----------+
                                 |
                            EventBus (Observer)
                                 |
                            AutoBidEngine
                            LifecycleScheduler
```

3 module Maven:

| Module | Vai trò |
|---|---|
| `shared` | Domain entity (User, Item, Auction, Bid…), protocol DTO, codec, event |
| `server` | DAO + Service + RequestRouter + Scheduler + AutoBid + AuthService |
| `client` | JavaFX App + ServerConnection + AuctionClient + Controllers + FXML |

## Database schema

5 bảng (xem chi tiết trong `database/auction_db.sql`):

- `users`: id, username, email, password (BCrypt), role, account_balance, seller_rating, access_level
- `items`: Single Table Inheritance — id, name, starting_price, category + cột phụ cho từng loại (brand/artist/make...)
- `auctions`: id, item_id, seller_id, start/end_time, status enum, current_price, version (optimistic lock), winner_bidder_id
- `bid_transactions`: id, auction_id, bidder_id, bid_amount, bid_time
- `auto_bid_configs`: cho Phase 7

## Hướng dẫn setup

### Yêu cầu

- JDK 17+
- Maven 3.8+

(Không cần MySQL, không cần daemon DB. SQLite chạy ngay trong JVM.)

### 1. Build

```bash
cd /path/to/Online-Auction-System
mvn clean install -DskipTests
```

### 2. Chạy server

```bash
mvn -pl server exec:java -Dexec.mainClass=com.bt.server.controller.AuctionServer
```

Lần đầu chạy: server tự tạo file `auction.db` với schema + 6 user demo +
3 item + 1 phiên RUNNING.

**Schema migration tự động và an toàn:**
- Mọi lần start, server apply `schema.sql` (`CREATE TABLE IF NOT EXISTS`).
  Schema đã có không bị động vào.
- Seed data CHỈ được insert khi bảng `users` đang trống → restart bao
  nhiêu lần cũng không phá dữ liệu user.
- Khi schema thay đổi (thêm cột mới), code dùng `addColumnIfMissing` để
  migrate idempotent. Code cũ + DB cũ vẫn chạy được sau khi update.

### 3. Chạy client

Mở terminal mới (giữ server chạy):

```bash
mvn -pl client javafx:run
```

Đăng nhập với:

| User | Password | Role |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `alice_s` | `password` | SELLER |
| `bob_s` | `password` | SELLER |
| `charlie_b` | `password` | BIDDER |
| `dan_b` | `password` | BIDDER |
| `eve_b` | `password` | BIDDER |

Mở 2-3 client cùng lúc để xem realtime push hoạt động.

### Reset database (cẩn thận — chỉ khi thực sự cần)

⚠ Lệnh dưới sẽ XÓA TOÀN BỘ dữ liệu (user, item, auction, bid). Chỉ chạy
khi bạn muốn bắt đầu hoàn toàn từ đầu:

```bash
rm auction.db
```

Trong dev bình thường KHÔNG cần xóa DB — dù sửa code thế nào, schema
migration sẽ tự xử lý.

## Test

```bash
# Unit test (shared module — không cần DB)
mvn -pl shared test

# DAO smoke test (cần MySQL chạy + schema imported)
mvn -pl server exec:java -Dexec.mainClass=com.bt.server.dao.DaoSmokeTest

# Codec smoke test
mvn -pl shared exec:java -Dexec.mainClass=com.bt.shared.protocol.ProtocolSmokeTest
```

## Design Pattern đã dùng

| Pattern | Vị trí | Lý do |
|---|---|---|
| **Singleton** | `DatabaseConnection`, `AuctionEventBus`, `ServerConnection`, `Session` | Resource toàn cục cần truy cập từ nhiều nơi, chỉ tồn tại 1 instance |
| **Factory Method** | `ItemFactory` | Tạo Electronics/Art/Vehicle dựa trên category, không expose constructor cho caller |
| **Observer** | `AuctionObserver`, `AuctionEventBus`, `ConnectionObserver` | Realtime: nhiều client xem cùng phiên, đẩy event khi có bid |
| **Strategy** | `WinnerStrategy` + `HighestBidStrategy` | Cho phép thay logic chọn winner mà không sửa scheduler |
| **State machine** | `Auction.AuctionStatus` + `ALLOWED_TRANSITIONS` | Enforce vòng đời OPEN → RUNNING → FINISHED → PAID |
| **Producer-Consumer** | `ServerConnection` listener thread + dispatch | Tách thread network khỏi UI thread, dùng CompletableFuture |
| **DTO** | `AuctionDto`, `BidDto`, `LoginResponse`,… | Tránh leak entity nội bộ qua wire |

## Concurrency safety

- Bid path dùng pessimistic lock ở DB: `SELECT … FOR UPDATE` trong transaction (`BiddingTransactionDAO`). Dù 100 thread cùng bid lên 1 phiên, MySQL serialize, không lost update.
- `AuctionEventBus` dùng `ConcurrentHashMap` + `ConcurrentHashMap.newKeySet()`.
- `ServerConnection` write có `writeLock`, listener thread riêng đọc.
- `AuctionLifecycleScheduler` dùng `ScheduledExecutorService` single-thread daemon.

## Phân công công việc

(điền tên thành viên + phần đảm nhiệm trước khi nộp)

## License

MIT — see [LICENSE](LICENSE).
