# Design Notes

Tài liệu giải thích các quyết định thiết kế quan trọng của hệ thống.

## 1. Module split

3 module Maven:

- **`shared`**: tất cả entity, DTO, codec, event interface, exception. Không
  phụ thuộc DB hay JavaFX. Cả server và client đều import.
- **`server`**: DAO (HikariCP + JDBC), service (auth, auction), event bus,
  scheduler, network router. Phụ thuộc `shared`.
- **`client`**: JavaFX App, controllers, FXML, network connection wrapper.
  Phụ thuộc `shared`.

Lý do tách: tránh client kéo theo MySQL driver / HikariCP, server không cần
JavaFX. `shared` đảm bảo wire format đồng nhất hai phía.

## 2. Entity hierarchy

```
Entity (abstract, Long id, Serializable)
├── User (abstract)
│   ├── Bidder    (accountBalance)
│   ├── Seller    (sellerRating)
│   └── Admin     (accessLevel)
├── Item (abstract)
│   ├── Electronics (brand, warrantyMonths)
│   ├── Art         (artist, yearCreated)
│   └── Vehicle     (make, model, mileage)
├── Auction         (state machine, anti-sniping)
└── BidTransaction  (Comparable theo timestamp)
```

- `Long id` nullable: null khi chưa persist, DB cấp lúc INSERT (AUTO_INCREMENT).
- `equals/hashCode` JPA-style: theo class + id, hai entity chưa có id chỉ
  bằng chính nó (reference). Tránh bug khi cho vào HashSet trước khi save.
- Mọi entity Serializable + serialVersionUID — sẵn sàng cho object stream
  hoặc cache.

## 3. Auction state machine

Khai báo bảng `ALLOWED_TRANSITIONS` tập trung thay vì rải `if status == X`
khắp code:

```
OPEN     → RUNNING | CANCELED
RUNNING  → FINISHED | CANCELED
FINISHED → PAID | CANCELED
PAID     → (terminal)
CANCELED → (terminal)
```

`transitionTo()` validate dựa vào bảng — thêm trạng thái mới chỉ cần update
bảng + thêm method tiện lợi (start/finish/markPaid/cancel).

## 4. Concurrency

### Vấn đề
Nhiều client bid đồng thời lên cùng 1 phiên. Có thể xảy ra:
- Lost update: bid B đè lên bid A dù A đã commit
- Two winners: hai bid cùng nhận được "bạn đang dẫn đầu"
- currentPrice bị rollback

### Giải pháp: Pessimistic lock ở DB
`BiddingTransactionDAO.placeBidAtomic` mở 1 transaction:
1. `SELECT … FOR UPDATE` trên row auctions → khóa row
2. Validate (status RUNNING, amount > current, không phải seller)
3. INSERT bid_transactions
4. UPDATE auctions current_price + version + 1
5. COMMIT

Mọi thread khác cùng auction_id sẽ bị MySQL block ở bước 1 cho tới khi
transaction trước commit/rollback. Không thể xảy ra lost update.

Cột `version` chuẩn bị cho optimistic locking nếu sau này muốn relax xuống
(vd cho read-heavy workload).

### In-memory protection
Object `Auction` trong RAM có `synchronized` cũ, đã được loại bỏ ở Phase 4
vì DB lock đã đảm bảo. RAM object giờ chỉ là cache, không phải nguồn truth.

## 5. Realtime push (Observer Pattern)

### Domain layer
- `AuctionObserver` interface (default methods cho từng loại event)
- `BidPlacedDomainEvent`, `AuctionFinishedDomainEvent`,...

### Server layer
- `AuctionEventBus`: subject, giữ map auctionId → set observer
- `ConnectionObserver`: bridge — nhận event domain, build wire-message,
  push qua `ClientConnection.sendMessage`

### Client layer
- `ServerConnection` có 1 listener thread đọc message liên tục
- Mỗi response match `requestId` với `CompletableFuture` đang chờ
- Mỗi event push (không có request gốc) forward tới `eventListeners`
- Controller JavaFX add listener, dùng `Platform.runLater` khi update UI

Tại sao không dùng `Observable` của Java cũ: deprecated từ Java 9, không
type-safe. Custom interface kèm default method cho clean.

## 6. Anti-sniping

`Auction.applyAntiSnipingIfNeeded` (trong RAM) và `BiddingTransactionDAO`
(persist): nếu bid xảy ra trong 30s cuối → cộng thêm 60s vào endTime.

Cài đặt cả 2 chỗ vì:
- DB cần biết để scheduler không finish sớm
- Object RAM cần biết cho UI countdown đúng

Cấu hình hằng số ở `Auction.DEFAULT_ANTI_SNIPE_WINDOW` và
`BiddingTransactionDAO.ANTI_SNIPE_*`. Nên đồng nhất — Phase mở rộng có thể
đọc từ config.

## 7. Auto-Bidding

`AutoBidEngine` đăng ký vào EventBus như một observer. Sau mỗi `BidPlaced`
(manual hoặc auto), engine tìm config có `maxBid` cao nhất, đăng ký sớm
nhất → bid hộ với `currentPrice + increment`. Loop tới khi không còn config
nào đủ điều kiện hoặc đạt max iterations.

PriorityQueue order: `(maxBid DESC, registeredAt ASC)`.

Edge case xử lý:
- Người vừa bid chính là chủ config → skip (không tự outbid)
- Bid hộ vượt quá `maxBid` → deactivate config
- Phiên đã đóng → catch `AuctionStateException` → dừng

## 8. Logging và config

- `application.properties` cho server (gitignore), `.example` commit
- Đọc qua `AppConfig` với 3 mức ưu tiên: env var > properties > default
- `logback.xml` 2 appender: console + rolling file (`logs/server.log`,
  giữ 14 ngày, tổng 200MB)
- Format ISO8601 cho file để tool như `lnav` parse được

## 9. Password security

`PasswordEncoder` wrap BCrypt:
- Cost factor 10 (~100ms hash)
- Salt tự sinh, embed vào hash
- Hỗ trợ migration: nếu DB có password plaintext (seed cũ), lần login
  thành công đầu tiên sẽ auto-rehash

Không bao giờ truyền password hash ra wire — `LoginResponse` không có
trường password.

## 10. CI/CD

`.github/workflows/ci.yml`:
- Trigger: push/PR vào main, develop
- Setup JDK 17 Temurin, cache Maven
- `mvn install -DskipTests` build toàn module
- `mvn -pl shared test` chạy unit test (server/client cần DB nên skip)
- Upload surefire-reports artifact (giữ 14 ngày)

Có thể mở rộng: dùng service container MySQL trong workflow để chạy DAO
test thật.

## 11. Trade-offs cần biết

- **Single Table Inheritance** cho `items`: đơn giản, nhanh load. Nhược
  điểm: nhiều cột NULL. Phù hợp cho 3 category. Nếu có >10 category thì
  nên Joined Table.
- **JSON line protocol**: dễ debug bằng telnet, dễ mở rộng. Nhược điểm: text
  > binary (Protobuf) ~ 2x. Đủ cho lưu lượng lớp học.
- **Single JVM server**: không scale horizontal. Để chạy multi-server cần
  dùng Redis pub/sub thay EventBus và sticky session cho subscription.
