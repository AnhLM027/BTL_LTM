# Fruit Battle Online

Tài liệu phân tích nghiệp vụ theo tác nhân, luồng xử lý, ngoại lệ và tiêu chí nghiệm thu: [NGHIEP_VU_GAME.md](NGHIEP_VU_GAME.md).

**Fruit Battle Online** là trò chơi đối kháng trực tuyến 1v1, được xây dựng bằng **JavaFX, Java TCP Socket và MySQL**. Hai người chơi thi đấu trong thời gian thực, điều khiển giỏ để hứng hoa quả, ghi điểm và sử dụng các kỹ năng đặc biệt nhằm gây khó khăn cho đối thủ.

Trò chơi có **một chế độ chơi duy nhất**, kết hợp giữa hứng hoa quả theo mục tiêu, né bom, tích lũy combo và sử dụng kỹ năng tấn công.

Mỗi trận kéo dài **60 giây**. Người có điểm số cao hơn khi kết thúc trận sẽ chiến thắng.

## 1. Các chức năng chính

### 1.1. Quản lý tài khoản

- Đăng ký và đăng nhập.
- Phân quyền người chơi (PLAYER) và quản trị viên (ADMIN).
- Xem và cập nhật thông tin cá nhân.
- Xem lịch sử thi đấu.
- Xem bảng xếp hạng người chơi.
- Quản trị viên có thể quản lý tài khoản và theo dõi thống kê hệ thống.

### 1.2. Ghép trận tự động

Trò chơi không sử dụng hệ thống tạo phòng hoặc mời người chơi.

- Người chơi nhấn **Tìm trận** tại màn hình chính.
- Server đưa người chơi vào hàng đợi ghép trận.
- Khi có đủ hai người chơi, server tự động ghép cặp.
- Hai client nhận thông tin đối thủ và dữ liệu trận đấu.
- Hệ thống đếm ngược 3 giây trước khi bắt đầu.
- Người chơi có thể hủy tìm trận khi chưa được ghép.

Mỗi tài khoản chỉ được tham gia một hàng đợi hoặc một trận đấu tại một thời điểm.

### 1.3. Gameplay

- Điều khiển giỏ bằng phím mũi tên trái và phải.
- Hứng đúng loại hoa quả mục tiêu để ghi điểm.
- Tránh hứng sai loại quả hoặc bom.
- Tích lũy combo để nhận thêm điểm thưởng.
- Tích lũy năng lượng để sử dụng kỹ năng.
- Sử dụng 3 kỹ năng tấn công gây bất lợi cho đối thủ.
- Theo dõi điểm số, combo, năng lượng và thời gian theo thời gian thực.

## 2. Luật chơi

### 2.1. Bắt đầu trận đấu

Sau khi ghép thành công, server khởi tạo trận đấu với các quy tắc:

- Thời gian mỗi trận là 60 giây.
- Mỗi người chơi có một sân chơi riêng và điều khiển một giỏ.
- Hoa quả và bom rơi từ trên xuống.
- Hai người chơi có cùng mục tiêu và cùng lịch sinh vật thể cơ bản để đảm bảo công bằng.
- Vật thể xuất hiện do kỹ năng tấn công chỉ được bổ sung vào sân của người bị tấn công.
- Server quản lý toàn bộ trạng thái trận đấu.

### 2.2. Cơ chế hứng hoa quả

Trong trận, hệ thống hiển thị một loại hoa quả mục tiêu, chẳng hạn như táo, chuối hoặc nho.

Người chơi cần điều khiển giỏ để hứng đúng loại quả được yêu cầu.

**Quy tắc tính điểm:**

| Hành động | Điểm |
|---|---:|
| Hứng đúng quả mục tiêu | +10 |
| Hứng sai loại quả | -5 |
| Hứng bom | -10 |
| Bỏ lỡ quả mục tiêu | 0 |
| Đạt mỗi 5 lần hứng đúng liên tiếp | +20 |

Điểm có thể âm nếu người chơi hứng sai hoặc hứng bom nhiều lần.

### 2.3. Thay đổi mục tiêu

Cứ sau 15 giây, server thay đổi loại hoa quả mục tiêu cho cả hai người chơi.

Ví dụ:

- 0–15 giây: Táo.
- 15–30 giây: Chuối.
- 30–45 giây: Nho.
- 45–60 giây: Táo.

Mục tiêu mới được lựa chọn ngẫu nhiên nhưng không trùng với mục tiêu ngay trước đó.

Các vật thể đang rơi vẫn tiếp tục di chuyển. Việc tính đúng hoặc sai dựa trên mục tiêu tại thời điểm hứng.

### 2.4. Tăng độ khó

Tốc độ rơi và mật độ xuất hiện vật thể tăng dần theo thời gian.

| Giai đoạn | Thời gian | Độ khó |
|---|---|---|
| 1 | 0–20 giây | Chậm |
| 2 | 20–40 giây | Trung bình |
| 3 | 40–60 giây | Nhanh |

Khoảng thời gian sinh vật thể dự kiến từ 0,7 đến 1,2 giây, tùy theo từng giai đoạn.

## 3. Hệ thống combo và năng lượng

### 3.1. Combo

Combo được tính dựa trên số lần hứng đúng liên tiếp.

- Hứng đúng: Combo tăng 1.
- Hứng sai hoặc hứng bom: Combo trở về 0.
- Mỗi khi combo đạt bội số của 5: Thưởng thêm 20 điểm.
- Bỏ lỡ hoa quả không làm mất combo.

Ví dụ: Hứng đúng 10 quả liên tiếp sẽ nhận tổng cộng 40 điểm thưởng combo.

### 3.2. Năng lượng

Năng lượng dùng để kích hoạt các kỹ năng tấn công.

- Bắt đầu trận với 0 năng lượng.
- Hứng đúng quả mục tiêu: +10 năng lượng.
- Hứng sai hoặc hứng bom: Không nhận năng lượng.
- Năng lượng tối đa: 100.
- Sử dụng kỹ năng sẽ tiêu hao năng lượng tương ứng.

Năng lượng được server quản lý và đồng bộ với client.

## 4. Hệ thống kỹ năng tấn công

Trò chơi có 3 kỹ năng chính. Người chơi sử dụng phím `1`, `2`, `3` để kích hoạt.

### 4.1. Bomb Attack – Gửi bom

- Phím sử dụng: `1`.
- Năng lượng tiêu hao: 30.
- Tạo thêm một quả bom trong sân đối thủ.
- Nếu đối thủ hứng phải bom sẽ bị trừ 10 điểm và mất combo.
- Bom xuất hiện sau một cảnh báo ngắn để đối thủ có thể né tránh.

### 4.2. Freeze Attack – Làm chậm

- Phím sử dụng: `2`.
- Năng lượng tiêu hao: 50.
- Giảm 40% tốc độ di chuyển giỏ của đối thủ.
- Thời gian hiệu lực: 3 giây.
- Sau khi hết hiệu lực, tốc độ giỏ trở về bình thường.

### 4.3. Giỏ nam châm

- Phím sử dụng: `3`; tiêu hao 90 năng lượng.
- Gắn nam châm lên giỏ trong 5 giây; chiều rộng vùng hứng giữ nguyên 100 đơn vị.
- Chỉ quả đang là mục tiêu trong bán kính 120 đơn vị mới bị kéo nhẹ theo chiều ngang về tâm giỏ; người chơi vẫn phải tự di chuyển để hứng.
- Bom và quả sai giữ quỹ đạo bình thường; kỹ năng không thay đổi tốc độ rơi.
- Có thể dùng khi đang chịu kỹ năng tấn công. Hồi chiêu chung 6 giây, không cộng dồn hay làm mới hiệu lực.

### 4.4. Quy tắc sử dụng kỹ năng

- Chỉ sử dụng khi đủ năng lượng.
- Thời gian hồi chiêu chung là 6 giây.
- Không thể kích hoạt kỹ năng mới khi kỹ năng trước còn hiệu lực.
- Hiệu ứng tấn công không cộng dồn. Nam châm là hỗ trợ bản thân và có thể dùng khi đang bị tấn công.
- Server kiểm tra năng lượng, hồi chiêu và trạng thái trận trước khi xử lý.
- Người bị tấn công nhận thông báo về hiệu ứng đang chịu.

## 5. Giao diện trò chơi

### 5.1. Màn hình đăng nhập và đăng ký

- Nhập tài khoản và mật khẩu.
- Đăng ký tài khoản mới.
- Hiển thị thông báo đăng nhập thành công hoặc thất bại.

### 5.2. Màn hình chính

Bao gồm:

- Nút **Tìm trận**.
- Thông tin tài khoản.
- Hồ sơ cá nhân.
- Lịch sử thi đấu.
- Bảng xếp hạng.
- Đăng xuất.

Khi nhấn Tìm trận, màn hình hiển thị trạng thái đang tìm đối thủ và nút Hủy tìm trận.

### 5.3. Màn hình gameplay

Bao gồm:

- Sân chơi hiển thị giỏ, hoa quả và bom.
- Tên và điểm số của hai người chơi.
- Thời gian còn lại.
- Loại hoa quả mục tiêu.
- Chỉ số combo.
- Thanh năng lượng.
- Ba kỹ năng tấn công và trạng thái hồi chiêu.
- Thông báo khi sử dụng hoặc nhận hiệu ứng kỹ năng.

Mỗi sân chơi sử dụng hệ tọa độ logic 800 × 600. Client có thể co giãn giao diện nhưng không làm thay đổi tọa độ xử lý của server.

### 5.4. Màn hình kết quả

Sau khi kết thúc trận, hiển thị:

- Người chiến thắng hoặc kết quả hòa.
- Điểm số của hai người chơi.
- Số quả hứng đúng và hứng sai.
- Số bom đã hứng.
- Combo cao nhất.
- Số lần sử dụng kỹ năng.
- Nút **Chơi tiếp** để tìm trận mới.
- Nút **Về trang chủ**.

### 5.5. Màn hình quản trị

Dành cho tài khoản ADMIN:

- Xem danh sách tài khoản.
- Khóa hoặc mở khóa tài khoản.
- Xem thông tin và thống kê trận đấu.
- Theo dõi tình trạng hoạt động của hệ thống.

## 6. Kiến trúc hệ thống

Hệ thống sử dụng mô hình **Client–Server**, giao tiếp thông qua TCP Socket bằng các message JSON.

### 6.1. Client – JavaFX

Client chịu trách nhiệm:

- Hiển thị các màn hình của trò chơi.
- Nhận thao tác bàn phím.
- Gửi yêu cầu tìm trận, hủy tìm trận.
- Gửi thao tác di chuyển và yêu cầu sử dụng kỹ năng.
- Nhận trạng thái trận từ server.
- Hiển thị vật thể, điểm số và hiệu ứng.

Client không tự quyết định điểm số, năng lượng hoặc kết quả trận.

### 6.2. Server – Java TCP Socket

Server chịu trách nhiệm:

- Quản lý tài khoản và xác thực người dùng.
- Quản lý danh sách người chơi trực tuyến.
- Quản lý hàng đợi ghép trận tự động.
- Khởi tạo và điều khiển trận đấu.
- Sinh hoa quả và bom.
- Quản lý vị trí giỏ và xác thực va chạm.
- Tính điểm, combo và năng lượng.
- Kiểm tra và thực hiện kỹ năng.
- Đồng bộ trạng thái giữa hai client.
- Xử lý mất kết nối và kết nối lại.
- Lưu kết quả trận đấu vào cơ sở dữ liệu.

### 6.3. Cơ sở dữ liệu – MySQL

Lưu trữ:

- Tài khoản và thông tin người chơi.
- Trận đấu và kết quả.
- Điểm số và thống kê.
- Lịch sử thi đấu.
- Bảng xếp hạng.

Không cần bảng quản lý phòng chơi vì hệ thống sử dụng ghép trận tự động.

Thiết kế quan hệ chi tiết gồm 4 bảng nghiệp vụ được mô tả trong [DATABASE.md](DATABASE.md), kèm sơ đồ ERD và cách chuyển dữ liệu từ cấu trúc cũ.

## 7. Giao tiếp mạng

Client và server trao đổi thông tin qua các message JSON.

| Message | Chức năng |
|---|---|
| `REGISTER` | Đăng ký tài khoản |
| `LOGIN` | Đăng nhập |
| `MATCH_FIND` | Bắt đầu tìm trận |
| `MATCH_CANCEL` | Hủy tìm trận |
| `MATCH_FOUND` | Ghép trận thành công |
| `MATCH_START` | Bắt đầu trận |
| `PLAYER_MOVE` | Gửi thao tác di chuyển |
| `FRUIT_SPAWN` | Đồng bộ vật thể |
| `TARGET_CHANGE` | Thay đổi quả mục tiêu |
| `CATCH_EVENT` | Thông báo hứng vật thể |
| `SCORE_UPDATE` | Cập nhật điểm, combo và năng lượng |
| `USE_SKILL` | Yêu cầu kích hoạt kỹ năng |
| `SKILL_EFFECT` | Đồng bộ hiệu ứng |
| `GAME_STATE` | Đồng bộ trạng thái |
| `MATCH_REJOIN` | Khôi phục trận sau mất kết nối |
| `MATCH_RESULT` | Trả về kết quả trận |

Server xác thực các thao tác dựa trên trạng thái thực tế, không tin tưởng điểm số hoặc kết quả va chạm do client tự cung cấp.

## 8. Xử lý mất kết nối

- Nếu người chơi mất kết nối khi đang tìm trận, server xóa họ khỏi hàng đợi.
- Nếu mất kết nối trước khi bắt đầu trận, server hủy lượt ghép và đưa người còn lại về hàng đợi.
- Nếu mất kết nối trong trận, trận vẫn tiếp tục đếm thời gian.
- Người chơi có thể đăng nhập lại và tiếp tục trận đang diễn ra.
- Server gửi lại điểm số, combo, năng lượng, vị trí giỏ, vật thể và các hiệu ứng còn hiệu lực.
- Nếu trận đã kết thúc, server trả về kết quả trận đấu.

## 9. Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| Ngôn ngữ | Java 17+ |
| Giao diện | JavaFX |
| Giao tiếp | TCP Socket |
| Định dạng dữ liệu | JSON |
| Cơ sở dữ liệu | MySQL 8+ |
| Kết nối database | JDBC |
| Quản lý thư viện | Maven |
| Môi trường phát triển | IntelliJ IDEA / NetBeans |

## 10. Quy trình hoạt động

1. Người chơi đăng ký hoặc đăng nhập.
2. Tại màn hình chính, nhấn **Tìm trận**.
3. Server đưa người chơi vào hàng đợi.
4. Khi đủ hai người, server ghép trận tự động.
5. Hệ thống đếm ngược 3 giây.
6. Trận đấu bắt đầu, hai người điều khiển giỏ hứng hoa quả để ghi điểm.
7. Người chơi tích lũy combo và năng lượng.
8. Sử dụng Bomb Attack, Freeze Attack hoặc Speed Attack để gây khó khăn cho đối thủ.
9. Mục tiêu và độ khó thay đổi theo thời gian.
10. Sau 60 giây, server xác định người thắng và lưu kết quả.
11. Người chơi chọn Chơi tiếp hoặc quay về màn hình chính.

## 11. Mục tiêu dự án

Fruit Battle Online được xây dựng nhằm tạo ra một trò chơi đối kháng 1v1 có luật chơi đơn giản nhưng mang tính cạnh tranh, kết hợp giữa phản xạ, ghi điểm và sử dụng kỹ năng chiến thuật.

Dự án tập trung vào việc vận dụng các kiến thức lập trình mạng:

- Xây dựng hệ thống Client–Server bằng TCP Socket.
- Xử lý nhiều kết nối đồng thời.
- Ghép trận tự động giữa hai người chơi.
- Trao đổi dữ liệu bằng JSON.
- Đồng bộ gameplay theo thời gian thực.
- Xác thực va chạm, điểm số và kỹ năng phía server.
- Xử lý mất kết nối và khôi phục trạng thái.
- Quản lý tài khoản và lưu trữ kết quả bằng MySQL.

Trò chơi không cần tạo phòng hay mời bạn bè. Người chơi chỉ cần nhấn Tìm trận để được ghép đối thủ tự động, sau đó thi đấu bằng cách hứng hoa quả, tích lũy năng lượng và sử dụng ba kỹ năng tấn công nhằm giành chiến thắng.
