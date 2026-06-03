> **Bài tập lớn môn Lập trình nâng cao (LTNC) — Nhóm 15**

---

## 1. Mô tả bài toán và phạm vi hệ thống

**Elite Auction** là một hệ thống đấu giá trực tuyến theo mô hình **Client–Server** thời gian thực. Hệ thống cho phép nhiều người dùng đồng thời tham gia vào các phiên đấu giá, đặt giá, theo dõi kết quả và thực hiện thanh toán.

Phạm vi hệ thống bao gồm ba vai trò chính:

| Vai trò | Mô tả |
|---------|-------|
| **Bidder (Người đặt giá)** | Tìm kiếm, xem, tham gia phòng đấu giá, đặt giá realtime, quản lý ví tiền |
| **Seller (Người bán)** | Đăng sản phẩm, tạo phiên đấu giá, quản lý phiên, xác nhận bán |
| **Admin (Quản trị viên)** | Duyệt/từ chối phiên đấu giá, quản lý người dùng, duyệt giao dịch tài chính |

---

## 2. Công nghệ sử dụng, môi trường chạy và yêu cầu cài đặt

### Ngôn ngữ & Framework

| Thành phần | Công nghệ |
|------------|-----------|
| Ngôn ngữ lập trình | Java 21 |
| Giao diện Client | JavaFX 21.0.2 + FXML |
| Kiến trúc Client | MVC (Controller–FXML–Model) |
| Kết nối mạng | Java Socket (TCP) + Object Serialization |
| Cơ sở dữ liệu | PostgreSQL (hosted trên Railway.app) |
| Connection Pool | HikariCP 5.1.0 |
| Build tool | Maven 3.x |
| Unit Testing | JUnit 5 + Mockito |
| CI/CD | GitHub Actions |

### Yêu cầu môi trường

- **JDK 21** trở lên (khuyến nghị: Eclipse Temurin 21)
- **Maven 3.8+** (hoặc dùng Maven Wrapper `./mvnw` có sẵn trong dự án)
- Kết nối Internet (để server kết nối PostgreSQL trên Railway)
- Hỗ trợ đa nền tảng: **Windows**, **Linux**, **macOS**

### Cài đặt JDK 21

**Windows / macOS:**  
Tải tại [https://adoptium.net](https://adoptium.net) → chọn **Temurin 21 (LTS)** → cài đặt bình thường.

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install -y openjdk-21-jdk
java -version  # Kiểm tra: phải hiện "21.x.x"
```

**Linux (Fedora/RHEL):**
```bash
sudo dnf install java-21-openjdk-devel
java -version
```

**macOS (Homebrew):**
```bash
brew install --cask temurin@21
java -version
```

> ⚠️ **Lưu ý**: Hệ thống sử dụng database PostgreSQL đã được deploy sẵn trên Railway. Bạn **không cần cài đặt PostgreSQL** trên máy local.

---

## 3. Cấu trúc thư mục

```
BTL_Nhom15_He_Thong_Dau_Gia_Truc_Tuyen/
├── .github/
│   └── workflows/
│       └── maven.yml               # CI/CD: tự động build & test khi push
├── .mvn/wrapper/
│   └── maven-wrapper.jar           # Maven Wrapper (không cần cài Maven tay)
│
├── common/                         # Module dùng chung (Client & Server)
│   └── src/main/java/com/auction/common/
│       ├── model/                  # Các lớp domain (User, Auction, Item, ...)
│       │   ├── Entity.java         # Lớp trừu tượng gốc (có id)
│       │   ├── User.java           # Abstract: chứa thông tin người dùng chung
│       │   ├── Admin.java          # Kế thừa User (role=ADMIN)
│       │   ├── Seller.java         # Kế thừa User (role=SELLER)
│       │   ├── Bidder.java         # Kế thừa User (role=BIDDER)
│       │   ├── Item.java           # Abstract: sản phẩm đấu giá (VEHICLE/ART/ELECTRONICS)
│       │   ├── Vehicle.java        # Kế thừa Item — xe cộ
│       │   ├── Art.java            # Kế thừa Item — nghệ thuật
│       │   ├── Electronics.java    # Kế thừa Item — điện tử
│       │   ├── Auction.java        # Phiên đấu giá
│       │   ├── BiddingHistory.java # Lịch sử đặt giá
│       │   ├── BidHistoryRow.java  # Hàng trong bảng lịch sử đặt giá
│       │   ├── Payment.java        # Thanh toán
│       │   ├── TransactionRequest  # Giao dịch nạp/rút tiền
│       │   └── IssueRecord.java    # Báo cáo sự cố
│       ├── network/                # DTO và giao thức truyền thông
│       │   ├── Message.java        # Gói tin trao đổi Client↔Server
│       │   ├── RequestCode.java    # Enum mã yêu cầu từ Client
│       │   ├── ResponseCode.java   # Enum mã phản hồi từ Server
│       │   ├── LoginDTO.java
│       │   ├── RegisterDTO.java
│       │   ├── BidPlaceDTO.java
│       │   ├── CreateAuctionDTO.java
│       │   ├── AuctionRoomDTO.java
│       │   ├── ReportIssueDTO.java
│       │   └── AuctionItemDTO.java
│       ├── factory/
│       │   ├── ItemFactory.java    # Factory Pattern: tạo Item đúng kiểu từ ResultSet
│       │   └── UserFactory.java    # Factory Pattern: tạo User đúng vai trò
│       └── exception/
│           ├── AuctionException.java
│           └── ErrorCode.java
│
├── server/                         # Module Server (thuần Java, không JavaFX)
│   └── src/main/java/com/auction/server/
│       ├── core/
│       │   ├── ServerMain.java         # Entry point server (port 8888)
│       │   ├── AuctionServer.java      # Lắng nghe kết nối TCP
│       │   ├── ClientHandler.java      # Xử lý mỗi client trong thread riêng
│       │   ├── AuctionRoom.java        # Phòng đấu giá realtime (thread-safe)
│       │   ├── AuctionRoomManager.java # Quản lý tất cả phòng đang chạy
│       │   ├── SessionManager.java     # Quản lý phiên đăng nhập
│       │   ├── RequestDispatcher.java  # Điều phối request đến đúng Service
│       │   └── HeartbeatMonitor.java   # Phát hiện client mất kết nối
│       ├── service/
│       │   ├── UserService.java        # Xử lý đăng nhập, đăng ký, hồ sơ
│       │   ├── BiddingService.java     # Logic đặt giá (thread-safe, rollback)
│       │   ├── AuctionService.java     # Quản lý vòng đời phiên đấu giá
│       │   ├── SellerService.java      # Nghiệp vụ dành cho Seller
│       │   ├── AdminService.java       # Nghiệp vụ dành cho Admin
│       │   ├── PaymentService.java     # Xử lý nạp/rút tiền
│       │   ├── TransactionService.java # Quản lý giao dịch
│       │   ├── ItemService.java        # Quản lý sản phẩm
│       │   └── ManagerService.java     # Nghiệp vụ tổng hợp
│       └── dao/
│           ├── DBConnection.java       # HikariCP connection pool → Railway PostgreSQL
│           ├── UserDAO.java
│           ├── AuctionDAO.java
│           ├── ItemDAO.java
│           ├── BidDAO.java
│           ├── BiddingHistoryDAO.java
│           ├── PaymentDAO.java
│           ├── TransactionDAO.java
│           └── IssueDAO.java
│   └── test/java/                  # Unit Test (JUnit 5 + Mockito)
│       └── com/auction/...         # ~20 file test cho service, dao, core
│
├── client/                         # Module Client (JavaFX)
│   └── src/main/java/com/auction/client/
│       ├── core/
│       │   ├── Launcher.java           # Entry point thực sự (tránh lỗi JavaFX module)
│       │   ├── ClientMain.java         # JavaFX Application, kết nối server khi khởi động
│       │   ├── SocketClient.java       # Singleton: quản lý kết nối TCP tới server
│       │   ├── MessageRouter.java      # Định tuyến response từ server đến đúng controller
│       │   ├── ClientSession.java      # Lưu thông tin user đang đăng nhập
│       │   └── HeartbeatSender.java    # Gửi PING định kỳ giữ kết nối
│       └── controller/
│           ├── auth/                   # Đăng nhập, đăng ký, quên mật khẩu
│           ├── bidder/                 # Giao diện người đặt giá
│           ├── seller/                 # Giao diện người bán
│           └── admin/                  # Giao diện quản trị viên
│   └── src/main/resources/view/    # FXML + CSS
│
└── pom.xml                         # Root POM (multi-module Maven project)
```

---

## 4. Câu lệnh dòng lệnh để chạy chương trình

> **Lưu ý**: Tất cả lệnh dưới đây đều phải chạy từ **thư mục gốc** của dự án (nơi có file `pom.xml`).

### Bước 1: Clone và build toàn bộ dự án

```bash
git clone https://github.com/nhombaitaplon15/BTL_Nhom15_He_Thong_Dau_Gia_Truc_Tuyen.git
cd BTL_Nhom15_He_Thong_Dau_Gia_Truc_Tuyen
```

**Windows:**
```cmd
mvnw.cmd clean compile -DskipTests
```

**Linux / macOS:**
```bash
chmod +x mvnw
./mvnw clean compile -DskipTests
```

> Nếu đã cài Maven sẵn, thay `mvnw.cmd` / `./mvnw` bằng `mvn`.

---

### Bước 2: Chạy Server

Mở **Terminal 1**, `cd` vào thư mục gốc dự án, rồi chạy:

**Windows:**
```cmd
mvnw.cmd exec:java -Dexec.mainClass="com.auction.server.core.ServerMain"
```

**Linux / macOS:**
```bash
./mvnw exec:java -Dexec.mainClass="com.auction.server.core.ServerMain"
```

Khi server khởi động thành công, terminal sẽ hiển thị:
```
====================================
  ELITE AUCTION SERVER STARTED
  Port: 8888
====================================
```

> ⚠️ **Giữ Terminal 1 mở**. Server phải đang chạy trước khi khởi động client.

---

### Bước 3: Chạy Client

Mở **Terminal 2 mới** (Terminal 1 vẫn giữ nguyên), `cd` vào thư mục gốc dự án, rồi chạy:

**Windows:**
```cmd
mvnw.cmd javafx:run
```

**Linux / macOS:**
```bash
./mvnw javafx:run
```

Cửa sổ JavaFX sẽ xuất hiện với màn hình đăng nhập **Elite Auction**.

> 💡 **Chạy nhiều client cùng lúc**: Mở thêm Terminal 3, Terminal 4... và lặp lại lệnh `javafx:run` ở mỗi terminal — mỗi cửa sổ là một người dùng độc lập.

---

### Chạy Unit Test

**Windows:**
```cmd
mvnw.cmd clean test
```

**Linux / macOS:**
```bash
./mvnw clean test
```

---

## 5. Hướng dẫn chạy Server/Client theo thứ tự cụ thể

```
Bước 1  →  Mở Terminal 1: Chạy Server (cổng 8888)
              Server kết nối PostgreSQL trên Railway tự động

Bước 2  →  Mở Terminal 2: Chạy Client lần 1 (Bidder / Seller)
              Cửa sổ JavaFX hiện màn hình đăng nhập

Bước 3  →  Mở Terminal 3 (tùy chọn): Chạy Client lần 2 (client khác)
              Để test đồng thời: một Seller tạo phiên, một Bidder đặt giá

Bước 4  →  Mở Terminal 4 (tùy chọn): Chạy Client lần 3 (Admin)
              Đăng nhập bằng tài khoản Admin để duyệt phiên đấu giá

→  Mỗi terminal Client = một người dùng riêng, tất cả kết nối song song đến Server
```

### Thứ tự sử dụng hệ thống (flow cơ bản)

1. **Seller** đăng nhập → Thêm sản phẩm mới → Tạo yêu cầu phiên đấu giá
2. **Admin** đăng nhập → Vào trang quản lý đấu giá → **Duyệt phiên** (OPEN)
3. **Bidder** đăng nhập → Vào danh sách phòng → **Tham gia phòng** → Đặt giá
4. Khi hết giờ, server tự kết thúc phiên → **Admin** tạo giao dịch → **Bidder** thanh toán
5. **Seller** xác nhận bán thành công

---

## 6. Danh sách chức năng đã hoàn thành

### ✅ Thiết kế lớp và cây kế thừa

- [x] Lớp trừu tượng `User` ← `Admin`, `Seller`, `Bidder` (Inheritance + Polymorphism)
- [x] Lớp trừu tượng `Item` ← `Vehicle`, `Art`, `Electronics` (Inheritance + Abstraction)
- [x] `Entity` là lớp gốc chứa `id` cho tất cả domain objects
- [x] Encapsulation đầy đủ (private fields + getters/setters)
- [x] Áp dụng **Factory Pattern**: `ItemFactory`, `UserFactory` để tạo đối tượng đúng kiểu từ database mà không cần if-else dài trong DAO
- [x] Áp dụng **Observer Pattern**: `AuctionRoom` broadcast trạng thái realtime đến tất cả viewer (khi có bid mới, có người vào/ra phòng)
- [x] Áp dụng **Singleton Pattern**: `SocketClient.getInstance()`, `DBConnection` (HikariCP)
- [x] Áp dụng **MVC Pattern** toàn diện ở client: FXML (View) + Controller + Model (common)
- [x] Áp dụng **DAO Pattern** ở server: tách biệt hoàn toàn tầng truy cập dữ liệu

### ✅ Chức năng chính

**Xác thực (Auth):**
- [x] Đăng nhập phân quyền (Admin / Seller / Bidder)
- [x] Đăng ký tài khoản mới (role Bidder/Seller)
- [x] Quên mật khẩu (xác minh qua số điện thoại)
- [x] Đổi mật khẩu
- [x] Đăng xuất (ngắt kết nối gracefully)
- [x] Heartbeat: tự động phát hiện và xử lý client mất kết nối

**Bidder:**
- [x] Xem danh sách tất cả phòng đấu giá đang mở (RUNNING)
- [x] Tham gia / rời phòng đấu giá realtime
- [x] **Đặt giá (Place Bid)** trong phòng — cập nhật tức thì đến tất cả client trong phòng
- [x] Chat trong phòng đấu giá
- [x] Xem chi tiết sản phẩm đang đấu giá
- [x] Xem lịch sử đặt giá của bản thân
- [x] Quản lý ví: nạp tiền, rút tiền
- [x] Xem lịch sử giao dịch tài chính
- [x] Thanh toán sau khi thắng phiên đấu giá
- [x] Cập nhật hồ sơ cá nhân
- [x] Gửi báo cáo sự cố cho Admin

**Seller:**
- [x] Thêm sản phẩm mới (3 loại: Xe cộ / Nghệ thuật / Điện tử)
- [x] Xem danh sách sản phẩm của mình
- [x] Tạo phiên đấu giá từ sản phẩm có sẵn
- [x] Quản lý phiên đấu giá: xem trạng thái (WAITING/OPEN/RUNNING/FINISHED/SOLD/REJECTED)
- [x] Chỉnh sửa thông tin phiên chưa được duyệt
- [x] Hủy phiên đấu giá
- [x] Xác nhận bán sau khi phiên kết thúc thành công
- [x] Quản lý tài khoản cá nhân

**Admin:**
- [x] Xem và duyệt / từ chối yêu cầu phiên đấu giá
- [x] Phong tỏa (Block) phiên đang chạy khi vi phạm
- [x] Quản lý người dùng: xem danh sách, ban / unban tài khoản
- [x] Quản lý giao dịch tài chính: xem, duyệt, từ chối
- [x] Tạo giao dịch từ phiên đã kết thúc
- [x] Xem toàn bộ báo cáo sự cố từ người dùng
- [x] Xóa phiên bị block sau khi xử lý

### ✅ Kỹ thuật quan trọng & Concurrency

- [x] **Xử lý đấu giá đồng thời an toàn**: `AuctionRoom` dùng `AtomicReference<Double>` cho giá hiện tại, `ConcurrentHashMap` cho danh sách viewer, `SingleThreadExecutor` để tuần tự hóa lệnh đặt giá → tránh race condition, lost update, dirty read
- [x] **Realtime update** cho tất cả client trong phòng khi có bid mới (Observer/broadcast qua Socket)
- [x] **Rollback** nếu đặt giá thất bại (không đủ tiền, giá thấp hơn hiện tại...)
- [x] **HeartbeatMonitor** trên server: tự động ngắt và dọn dẹp client không phản hồi
- [x] Mỗi client được xử lý bởi một thread độc lập (`ClientHandler`)

### ✅ Tích hợp, kiến trúc & chất lượng mã

- [x] Kiến trúc Client–Server rõ ràng, tách biệt hoàn toàn (Server không dùng JavaFX)
- [x] MVC ở client: FXML (View) + Controller (Controller) + common/model (Model)
- [x] Controller-Model-DAO ở server
- [x] Maven multi-module: `common` / `server` / `client`
- [x] **Unit Test (JUnit 5 + Mockito)**: có test cho Service, DAO, Core, Factory
- [x] **CI/CD GitHub Actions** (`maven.yml`): tự động `mvn clean verify` khi push/PR
- [x] HikariCP Connection Pool cho database (tối ưu hiệu năng)
- [x] Coding convention nhất quán, tên class/method có ý nghĩa rõ ràng

### ✅ Chức năng nâng cao (tùy chọn)

- [x] **Gia hạn phiên đấu giá (Anti-sniping)**: Tự động cộng thêm thời gian nếu có lượt đặt giá hợp lệ diễn ra ở sát những giây cuối cùng.

---

## 7. Link báo cáo và video demo

| Tài liệu | Link |
|----------|------|
| 📄 Báo cáo PDF | *(Điền link Google Drive / GitHub tại đây)* |
| 🎬 Video Demo | *(Điền link YouTube / Drive tại đây)* |

---

## Ghi chú kỹ thuật

- Server mặc định lắng nghe tại `localhost:8888`. Client kết nối tới `127.0.0.1:8888`.
- Database PostgreSQL đã được cấu hình sẵn trong `DBConnection.java` (Railway cloud). Không cần setup DB local.
- Nếu gặp lỗi `Connection refused` khi mở client, hãy đảm bảo **Server đã chạy trước**.
- Nếu gặp lỗi JavaFX trên Linux (headless server), đảm bảo chạy client trên máy có màn hình đồ họa hoặc dùng VNC.
