# Fruit Battle Online

Game đối kháng 1v1 bằng JavaFX, TCP Socket và MySQL. Giao diện theo ảnh mẫu dùng cảnh vườn trời xanh, khung gỗ có lá/hoa, chân dung nông dân, đồng hồ vàng và hai sân phủ rộng. Hình, sprite và chữ tiếng Việt được đóng gói sẵn. Có 22 SFX cùng hai nhạc nền chiptune riêng của dự án; game không tải hình/âm thanh lúc chơi. Luật chơi đầy đủ nằm trong [GAME.md](GAME.md).

## Chạy trên Windows

Cần **JDK 21+** và **MySQL 8.0.16+** đang chạy ở cổng 3306. JavaFX 21.0.8 được lấy từ bộ nhớ đệm Maven; script tải các JAR từ Maven Central nếu máy chưa có.

Mở PowerShell ở thư mục dự án, chạy server trước:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode server
```

Mở hai cửa sổ PowerShell khác, mỗi cửa sổ chạy một client:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode client
```

Đăng ký hai tài khoản khác nhau, đăng nhập rồi bấm **Tìm trận** ở cả hai cửa sổ. Server tự ghép cặp và đếm ngược 3 giây.

Chơi qua mạng LAN: chạy server trên một máy, nhập IP của máy đó ở màn hình đăng nhập trên hai client. Cổng mặc định là **5555**; có thể đổi bằng `-Port 6000` ở cả server và client. Máy chạy server cần cho phép kết nối TCP vào cổng đã chọn.

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode client -HostName 192.168.1.10 -Port 5555
```

## MySQL

Server tự tạo database `fruit_battle` và các bảng khi khởi động. Cấu hình mặc định theo yêu cầu dự án:

| Thuộc tính | Giá trị |
|---|---|
| JDBC URL | `jdbc:mysql://localhost:3306/fruit_battle` |
| User | `root` |
| Password | `123456` |

Cấu hình và vòng đời kết nối nằm trong `src/main/java/fruit/server/infrastructure/persistence/DatabaseConnection.java`; `Store` phụ trách truy vấn, transaction và lưu dữ liệu. Có thể ghi đè cấu hình trước khi chạy server:

```powershell
$env:FRUIT_DB_URL = 'jdbc:mysql://localhost:3306/fruit_battle?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Bangkok'
$env:FRUIT_DB_USER = 'root'
$env:FRUIT_DB_PASSWORD = '123456'
$env:FRUIT_ADMIN_PASSWORD = 'MatKhauAdminCuaBan'
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode server
```

Tài khoản quản trị ban đầu là `admin`. Nếu không đặt `FRUIT_ADMIN_PASSWORD`, server sinh mật khẩu ngẫu nhiên và in **một lần khi tạo admin**. Các lần chạy sau dùng tài khoản đã lưu. Mật khẩu tài khoản game được băm PBKDF2 với salt riêng; không lưu mật khẩu đăng nhập dạng rõ.

Trong bản đã chạy kiểm chứng trên máy này, mật khẩu admin ban đầu nằm trong `target/server.log`.

Schema có **đúng 4 bảng nghiệp vụ**: `accounts`, `player_stats`, `matches`, `match_players`. Xem [DATABASE.md](DATABASE.md) và [DDL](database/fruit_battle_schema.sql).

Chạy `run.ps1 -Mode database` để tạo mới hoặc kiểm tra cấu trúc 4 bảng hiện tại. Mã chuyển đổi từ bản 2/6 bảng đã được dọn sau khi database thật chuyển xong. Các tệp SQL sao lưu vẫn nằm trong `data/backups`. Database có cấu trúc khác sẽ được báo lỗi, không tự chuyển hoặc xóa dữ liệu.

Để thử trên máy không cài MySQL, có thể chủ động chọn `-LocalStore`; khi đó dữ liệu được lưu trong thư mục `data`. Chế độ mặc định luôn dùng MySQL và báo lỗi nếu kết nối thất bại.

## Điều khiển và luật chính

| Phím | Tác dụng |
|---|---|
| ← / → | Di chuyển giỏ |
| 1 | Gửi bom — 30 năng lượng |
| 2 | Làm chậm giỏ đối thủ 40% trong 3 giây — 50 năng lượng |
| 3 | Giỏ nam châm: hút nhẹ quả đúng mục tiêu trong bán kính 120 trong 5 giây — 90 năng lượng |
| F8 | Tắt/bật toàn bộ âm thanh |

Nút **Âm thanh** mở ba thanh âm lượng **Chung / Nhạc nền / Hiệu ứng**, cùng tùy chọn giảm chuyển động và rung sân. Các lựa chọn được lưu tại `data/client-settings.properties`. Không có thiết bị âm thanh vẫn chơi được.

Hứng quả có điểm nổi và particle; bom có nổ/rung riêng sân; làm chậm có băng quanh giỏ; nam châm làm giỏ rộng hơn, có quầng sáng và đếm ngược; combo và kết quả có cue riêng. Những phản hồi này lấy sự kiện từ server. Đăng nhập lại không phát lại các tiếng hứng quả cũ.

Mỗi trận 60 giây. Hứng đúng mục tiêu +10 điểm và +10 năng lượng; sai −5 điểm; bom −10 điểm. Mỗi 5 combo thưởng 20 điểm. Bỏ lỡ quả không làm mất combo. Mục tiêu đổi mỗi 15 giây; độ khó tăng sau 20 và 40 giây. Năng lượng tối đa 100; hồi chiêu chung 6 giây.

Đăng nhập lại sau mất kết nối để tiếp tục trận: đồng hồ trận vẫn chạy trên server. Khi trận đã kết thúc, client nhận kết quả.

## Mã nguồn

Code được tổ chức theo tính năng trong hai nhánh **client** và **server**. Các lớp Actions/Service xử lý logic; Screen dựng giao diện. Chi tiết ở [FEATURE_REFACTOR.md](FEATURE_REFACTOR.md).

```text
src/main/java/fruit/
├── common/                         Protocol, MessageData
├── client/
│   ├── Client.java                 Entry point JavaFX
│   ├── application/                Controller, state, view port, cửa sổ và điều hướng
│   ├── features/
│   │   ├── accounts/               AccountActions, LoginScreen, ProfileScreen
│   │   ├── matchmaking/            MatchmakingActions
│   │   ├── lobby/                  LobbyScreen
│   │   ├── gameplay/               GameplayActions, GameScreen, GameRenderer, feedback
│   │   ├── results/                ResultScreen
│   │   ├── history/                HistoryScreen
│   │   ├── leaderboard/            LeaderboardScreen
│   │   ├── admin/                  AdminActions, AdminScreen
│   │   ├── settings/               ClientAudio, AudioSettingsDialog
│   │   └── help/                   HelpDialog
│   ├── infrastructure/network/     ClientConnection
│   └── shared/                     ui: ảnh/font/theme/control, preview: DemoData
└── server/
    ├── Server.java                 Tham số chạy, khởi động và console
    ├── application/                GameServer, ServerDisplay
    ├── features/
    │   ├── accounts/               User, AccountService, AccountRepository
    │   ├── matchmaking/            MatchmakingService
    │   ├── matches/                Match, Player, Drop, MatchRepository
    │   ├── statistics/             StatisticsService: history/leaderboard
    │   └── admin/                  AdminService
    └── infrastructure/
    │   ├── network/                Session
    │   └── persistence/            Store, kết nối/schema/setup
```

Client gửi thao tác, server tính toán trạng thái. Điểm, va chạm, năng lượng và kết quả đều do server xác thực. Mỗi message là **một object JSON UTF-8 kết thúc bằng ký tự xuống dòng**, dùng `Socket`, `ServerSocket`, reader và writer thông thường. Ví dụ:

```json
{"type":"PLAYER_MOVE","direction":-1}
{"type":"USE_SKILL","skill":1}
{"type":"MATCH_FIND"}
```

## Build và kiểm tra

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode build
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode test
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode test -MySqlTest
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode ui-test
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode presentation-test
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode presentation-test -AudioDevice
```

Test dùng TCP thật và dữ liệu riêng, gồm cả trận đủ 60 giây nên cần chờ hơn một phút. File kiểm tra nằm ở [GameTest.java](src/test/java/fruit/server/application/GameTest.java). `-MySqlTest` tạo database kiểm tra riêng, không ghi vào tài khoản của người chơi. `ui-test` mở cửa sổ JavaFX và thao tác bằng API kiểm tra chuẩn, có mã tại [ClientTest.java](src/test/java/fruit/server/application/ClientTest.java).

`presentation-test` kiểm tra tài nguyên, chữ tiếng Việt, WAV, chống lặp sự kiện và lưu thiết lập. `-AudioDevice` phát tiếng thử để kiểm tra mixer thật, chuyển nhạc, mute và giới hạn clip. `ui-test` mở game và thao tác các màn hình, kỹ năng cùng bảng âm lượng.

Nguồn hình, font, âm thanh và cách tái tạo asset được ghi tại [ASSET_CREDITS.md](ASSET_CREDITS.md). Tài nguyên nằm trong `src/main/resources/fruit`; script chạy và Maven đều đưa chúng vào classpath.

Có thể mở `pom.xml` bằng IntelliJ IDEA và dùng Maven:

```text
mvn compile
mvn exec:java -Dexec.mainClass=fruit.server.Server
mvn javafx:run
mvn test
```

Ảnh kiểm tra giao diện có thể tạo bằng:

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode client -Snapshot target\game.png -Screen game
```
