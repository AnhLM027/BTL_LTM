# Fruit Battle Online

> Game đối kháng 1v1 bắt hoa quả, xây dựng bằng Java Swing, TCP socket và MySQL.

Fruit Battle Online là trò chơi thời gian thực dành cho hai người chơi. Mỗi trận kéo dài 30 giây; client chịu trách nhiệm hiển thị và gửi thao tác, còn server là nguồn dữ liệu chính để xác thực va chạm, tính điểm và đồng bộ trạng thái trận đấu.

## Tính năng chính

- Đăng ký, đăng nhập và phân quyền `PLAYER` / `ADMIN`.
- Sảnh chính, tạo phòng, mời người chơi và sẵn sàng vào trận.
- Hai chế độ chơi: `CLASSIC` và `ORDER`.
- Đồng bộ trận đấu qua TCP với message JSON.
- Server xác thực `CATCH_EVENT`, chống gửi lặp và tự tính điểm.
- Hiển thị hoa quả, bom, combo, thời gian và kết quả trận.
- Hồ sơ, lịch sử trận, xếp hạng và quản trị tài khoản.
- Hỗ trợ reconnect/rejoin trận đang diễn ra theo snapshot server.

## Luật chơi

### CLASSIC — Hứng né bom

- Server tạo 16 quả thường và 4 quả bom.
- Người chơi chỉ điều khiển một giỏ bằng phím `←` và `→`.
- Hứng quả thường: `+10` điểm.
- Hứng bom: `-10` điểm.
- Nhịp trận gồm 3 giai đoạn: khởi động, tăng tốc và tốc độ tối đa.
  Giữa hai giai đoạn có 2,2 giây không spawn vật thể; chỉ khi vật thể
  trước đó đã rơi hết, client mới hiển thị thông báo tăng tốc và đếm ngược.

### ORDER — Hứng theo đơn hàng

- Server chọn 3 loại quả khác nhau làm đơn hàng của trận.
- Server tạo 20 vật thể gồm quả thuộc đơn và quả ngoài đơn.
- Hứng quả thuộc đơn: `+10` điểm.
- Hứng quả ngoài đơn: `-5` điểm.
- Ba loại quả mục tiêu được hiển thị cố định trong giao diện gameplay.

### Combo

Mỗi lần hứng đúng liên tiếp làm tăng combo. Cứ đủ 5 lần hứng đúng, server phát sinh combo bonus và gửi lại trong `SCORE_UPDATE`. Client không tự quyết định điểm hoặc bonus.

## Kiến trúc

~~~text
┌──────────────────────┐       TCP / JSON       ┌──────────────────────┐
│ Java Swing Client    │ <────────────────────> │ Java TCP Game Server │
│ Login, lobby, game   │                        │ Auth, room, match    │
└──────────────────────┘                        └──────────┬───────────┘
                                                           │ JDBC
                                                           ▼
                                                  ┌────────────────────┐
                                                  │ MySQL               │
                                                  │ account, player,    │
                                                  │ room, game_match,   │
                                                  │ fruit_spawn, score  │
                                                  └────────────────────┘
~~~

Server là nơi duy nhất có quyền quyết định điểm, kết quả trận, trạng thái phòng và dữ liệu thống kê.

## Công nghệ

- Java 17+.
- Java Swing/AWT cho giao diện desktop.
- TCP socket và JSON message.
- MySQL 8+.
- JDBC Connector/J.
- Maven hoặc cấu hình thư viện tương đương trong IDE.

## Cấu trúc thư mục

~~~text
FruitBattleOnline/
├── database/
│   └── schema.sql
├── src/src/Client/       # Main client và các màn hình Swing
├── src/src/Server/       # TCP server, handler, service, repository
├── src/src/Shared/       # Model, enum và protocol dùng chung
├── src/src/Client/assets/ # Background, fruit, basket và icon
└── README.md
~~~

## Khởi tạo cơ sở dữ liệu

1. Tạo database và chạy toàn bộ `database/schema.sql` bằng MySQL 8+.
2. Schema tạo các bảng tài khoản, người chơi, phòng, trận đấu, vật thể rơi, điểm số, lịch sử và bảng xếp hạng.
3. Tạo tài khoản mẫu:

~~~sql
-- Mật khẩu mẫu của các tài khoản: 123456
-- Tài khoản ADMIN chỉ nằm trong bảng account.
-- PLAYER tương ứng với các bản ghi trong account và player.
~~~

Có thể dùng seed SQL đi kèm schema hoặc tạo tài khoản bằng chức năng đăng ký. Không lưu hoặc gửi `password_hash` về client.

## Cấu hình kết nối

Giá trị mặc định trong server:

~~~text
JDBC URL : jdbc:mysql://localhost:3306/fruit_battle_online
User     : root
Password : 123456
Port     : 12345
~~~

Nên thay đổi mật khẩu MySQL trong môi trường thực tế. Có thể ghi đè cấu hình bằng biến môi trường hoặc tham số JVM theo lớp `DatabaseConfig` và cấu hình server hiện tại.

## Chạy ứng dụng

### IntelliJ IDEA

1. Mở project và bảo đảm JDK cùng MySQL Connector/J đã được cấu hình.
2. Khởi động `Server.ServerMain` trước.
3. Khởi động một hoặc hai instance `Client.ClientMain`.
4. Đăng nhập bằng hai tài khoản khác nhau, tạo phòng, mời đối thủ, sẵn sàng và bắt đầu trận.

### Dòng lệnh

Biên dịch toàn bộ source theo cấu hình JDK/IDE, sau đó chạy server trước client. Client mặc định kết nối `127.0.0.1:12345`; nếu server chạy ở máy khác, cập nhật cấu hình TCP của client.

## Luồng gameplay

~~~text
LOGIN
  → ROOM_CREATE / ROOM_INVITE / ROOM_ACCEPT
  → MATCH_READY
  → MATCH_PREPARE
  → MATCH_START
  → FRUIT_SPAWN ... CATCH_EVENT
  → SCORE_UPDATE
  → MATCH_RESULT
  → quay về LobbyScreen
~~~

Các message quan trọng:

- `MATCH_PREPARE`: mode, thông tin trận và danh sách mục tiêu của `ORDER`.
- `FRUIT_SPAWN`: `fruitInstanceId`, `fruitId`, `isBomb`, `x`, `spawnOffsetMs`, `fallDurationMs`.
- `CATCH_EVENT`: chỉ gồm `matchId` và `fruitInstanceId`; client không gửi điểm.
- `SCORE_UPDATE`: điểm chính thức, `wasCorrect`, `comboCount`, `comboBonus`.
- `MATCH_REJOIN`: snapshot để khôi phục trận sau khi đăng nhập lại.
- `MATCH_RESULT`: kết quả, điểm cuối và dữ liệu lịch sử.

## Tọa độ và hiển thị

- Vùng logic gameplay luôn là `800 × 600`.
- JPanel có thể resize; client scale theo kích thước thực tế nhưng không thay đổi tọa độ server.
- Giỏ di chuyển trong vùng logic, không vượt ra ngoài màn hình.
- Asset được tải từ thư mục resource; khi thiếu asset, client sử dụng fallback rõ ràng thay vì làm hỏng trận.

## Bảo mật và tính nhất quán

- Server kiểm tra quyền trước mọi thao tác quản trị `ADMIN_*`.
- Server xác thực người gửi, trận, vật thể, thời gian và trạng thái trước khi chấp nhận `CATCH_EVENT`.
- Event đã xử lý không được ghi điểm lần hai.
- `password_hash` chỉ tồn tại phía server/database.
- Các thao tác kết thúc trận và cập nhật lịch sử được thực hiện trong transaction.

## Kiểm thử đề xuất

- Đăng ký/đăng nhập bằng hai client.
- Tạo phòng, mời, chấp nhận, từ chối và timeout.
- Kiểm tra cả `CLASSIC` và `ORDER`.
- Hứng đúng, hứng sai, hứng bom và gửi lặp `CATCH_EVENT`.
- Kiểm tra combo, điểm cuối, lịch sử và leaderboard.
- Ngắt kết nối giữa trận, đăng nhập lại và kiểm tra rejoin snapshot.
- Khởi động lại server khi trận ở `PLAYING` hoặc `FINALIZING`.
- Gửi `ADMIN_*` từ tài khoản `PLAYER` và bảo đảm server từ chối.

## Trạng thái phát triển

Nhánh phát triển hiện tại: `MA_Hoai`.

Khi triển khai production, hãy thay mật khẩu mặc định, cấu hình host/port riêng và bật log server ở mức phù hợp.
