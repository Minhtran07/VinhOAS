# Hướng dẫn cài đặt và chạy Online Auction System

Tài liệu này hướng dẫn dựng và chạy dự án trên **Windows / macOS / Linux** từ máy mới hoàn toàn.

> Hệ thống là JavaFX desktop client + TCP socket server. Server và client có thể chạy trên cùng 1 máy hoặc 2 máy khác nhau trong cùng mạng LAN.

---

## 1. Yêu cầu hệ thống

| Thành phần | Phiên bản tối thiểu | Ghi chú |
|------------|--------------------|---------|
| **JDK**   | 17 trở lên (khuyến nghị Temurin 17 hoặc 21) | Cần JDK đầy đủ, không phải JRE |
| **Maven** | 3.6.3 trở lên | Để build project |
| **Git**   | bất kỳ | Để clone source |
| **RAM**   | 1 GB trống | Server + 2 client cùng lúc |
| **Cổng**  | TCP 1234 (mặc định) | Có thể đổi trong `application.properties` |

JavaFX **không cần cài riêng** — Maven sẽ tự kéo về theo `pom.xml`. Native lib khớp CPU (Apple Silicon, x86_64, ARM Linux) cũng được Maven xử.

SQLite cũng **không cần cài** — driver `sqlite-jdbc` đã trong dependency, file `auction.db` được tạo tự động.

---

## 2. Cài JDK + Maven

### macOS (khuyến nghị dùng Homebrew)

```bash
# Cài Homebrew nếu chưa có
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Cài JDK 17 và Maven
brew install openjdk@17 maven

# Đăng ký JDK với hệ thống
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-17.jdk
echo 'export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
```

Verify:
```bash
java -version    # phải in "openjdk version 17.x.x" hoặc cao hơn
mvn -version
```

### Windows

1. Tải **Eclipse Temurin JDK 17** tại https://adoptium.net/temurin/releases/?version=17 → chọn `Windows x64 .msi` → cài đặt mặc định.
2. Tải **Maven** binary zip tại https://maven.apache.org/download.cgi → giải nén ra `C:\Program Files\apache-maven-3.9.x`.
3. Mở **Settings → System → About → Advanced system settings → Environment Variables**:
   - Tạo biến `JAVA_HOME` = `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`
   - Tạo biến `MAVEN_HOME` = `C:\Program Files\apache-maven-3.9.x`
   - Sửa biến `Path`, thêm 2 dòng:
     - `%JAVA_HOME%\bin`
     - `%MAVEN_HOME%\bin`
4. Mở **CMD mới** (quan trọng — terminal cũ không thấy biến mới) và verify:
   ```cmd
   java -version
   mvn -version
   ```

### Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk maven git
java -version
mvn -version
```

### Linux (Fedora/RHEL)

```bash
sudo dnf install -y java-17-openjdk-devel maven git
```

---

## 3. Lấy source

```bash
git clone https://github.com/Minhtran07/VinhOAS.git
cd VinhOAS
```

> Nếu thầy cô đưa file `.zip` thay vì link git, giải nén ra rồi `cd` vào folder.

---

## 4. Build dự án

Tại thư mục root (chỗ có `pom.xml` cha):

```bash
mvn clean install
```

Lần đầu sẽ tải về ~150 MB dependency (JavaFX, HikariCP, SQLite, Gson, JUnit…). Kết thúc phải thấy:

```
[INFO] BUILD SUCCESS
[INFO] Total time:  XX.XXX s
```

Nếu muốn skip integration test (chạy nhanh hơn):
```bash
mvn clean install -DskipTests
```

---

## 5. Chạy server và client

> **Quan trọng:** server **PHẢI** chạy trước client.

### macOS / Linux

**Terminal 1 — Server:**
```bash
./run-server.sh
```

**Terminal 2 — Client thứ nhất:**
```bash
./run-client.sh
```

**Terminal 3 — Client thứ hai (test multi-bidder):**
```bash
./run-client.sh
```

Nếu file `.sh` không có quyền thực thi:
```bash
chmod +x run-server.sh run-client.sh
```

### Windows

**CMD 1 — Server:**
```cmd
run-server.bat
```

**CMD 2 — Client:**
```cmd
run-client.bat
```

### Hoặc dùng Maven trực tiếp (mọi OS)

```bash
# Server
mvn -pl shared,server -am -DskipTests package
java -jar server/target/server-1.0-SNAPSHOT.jar

# Client (trong terminal khác)
mvn -pl client javafx:run
```

---

## 6. Đăng nhập thử

Server tự seed 6 tài khoản lần đầu start. Tất cả password là `password` (trừ admin):

| Username | Role | Password | Balance |
|----------|------|----------|---------|
| `admin` | ADMIN | `admin123` | — |
| `alice_s` | SELLER | `password` | — |
| `bob_s` | SELLER | `password` | — |
| `charlie_b` | BIDDER | `password` | 100,000,000 đ |
| `dan_b` | BIDDER | `password` | 200,000,000 đ |
| `eve_b` | BIDDER | `password` | 500,000,000 đ |

Sau khi đăng nhập:
- **Seller** (`alice_s`/`bob_s`): tạo item → tạo phiên đấu giá
- **Bidder** (`charlie_b`/`dan_b`/`eve_b`): vào phòng → đặt giá hoặc bật auto-bid → thanh toán + đánh giá khi thắng
- **Admin**: xem danh sách user, ban/unban

---

## 7. Cấu hình nâng cao

File `server/src/main/resources/application.properties`:

```properties
# Đổi đường dẫn DB
db.url=jdbc:sqlite:auction.db
db.pool.maxSize=4

# Đổi port server
server.port=1234
server.maxClients=100
```

Ưu tiên đọc: **biến môi trường > properties file > default**. Có thể override không cần sửa file:

```bash
# macOS/Linux
AUCTION_SERVER_PORT=5555 ./run-server.sh

# Windows
set AUCTION_SERVER_PORT=5555 && run-server.bat
```

### Client kết nối server từ máy khác

Sửa `client/src/main/resources/application.properties` (nếu có) hoặc set env:
```bash
AUCTION_SERVER_HOST=192.168.1.10 ./run-client.sh
```

Mặc định client kết nối `localhost:1234`.

---

## 8. Chạy test

```bash
mvn test
```

Sẽ chạy:
- **Unit test** ở `shared/`: validate User, Auction, Item, MessageCodec
- **Integration test concurrency** ở `server/`: 50 bidder song song, auto-bid race, scheduler race

Kết quả mong đợi: `Tests run: XX, Failures: 0`.

---

## 9. Troubleshooting

| Triệu chứng | Nguyên nhân & Fix |
|-------------|-------------------|
| `java: command not found` | JDK chưa cài hoặc chưa vào PATH. Verify bằng `java -version`. |
| `mvn: command not found` | Maven chưa cài. Xem mục 2. |
| `Address already in use: bind` (port 1234) | Server cũ còn chạy. Kill: `lsof -ti :1234 \| xargs kill` (macOS/Linux) hoặc `netstat -ano \| findstr :1234` rồi `taskkill /PID xxx /F` (Windows). |
| `Module javafx.controls not found` | Đang chạy `java -jar client.jar` thẳng. Phải dùng `mvn javafx:run` để Maven set module-path. |
| `[Server] Lỗi init DB` | File `auction.db` bị lock bởi process khác. `lsof auction.db` xem process nào, kill nó. |
| Client báo "Mất kết nối" liên tục | Server không chạy, hoặc firewall chặn port 1234. Tắt firewall thử lại. |
| `column paid_at: no such column` | Đang dùng DB cũ trước khi có migration. Xoá `auction.db`, restart server (sẽ seed lại). |
| `BUILD FAILURE Source option 17 is no longer supported` | JDK quá cũ (< 17). Update JDK theo mục 2. |
| Trên macOS: `xcrun: error: invalid active developer path` | Thiếu Command Line Tools. Cài: `xcode-select --install`. |
| Tiếng Việt hiển thị lỗi font | Đảm bảo terminal/IDE dùng UTF-8. Trên Windows CMD: `chcp 65001`. |

---

## 10. Cấu trúc thư mục

```
VinhOAS/
├── pom.xml              ← Parent POM (multi-module)
├── shared/              ← DTO, entity, protocol dùng chung
├── server/              ← Logic server: DAO, service, scheduler, autobid
├── client/              ← JavaFX UI + network client
├── run-server.sh/.bat
├── run-client.sh/.bat
├── README.md            ← Tổng quan
├── DESIGN.md            ← Kiến trúc & design patterns
├── CONTRIBUTING.md
└── LICENSE
```

---

## 11. Liên hệ

Tác giả: Minh Tran (`minhtran07.m@gmail.com`)
Repo: https://github.com/Minhtran07/VinhOAS

Báo issue/bug qua tab **Issues** trên GitHub.
