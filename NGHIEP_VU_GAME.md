# Nghiệp vụ trò chơi Fruit Battle Online

Tài liệu mô tả nghiệp vụ theo luật chơi và mã nguồn hiện tại của dự án, đối chiếu ngày 31/10/2026. Dùng để trình bày đề tài, phân tích chức năng và xây dựng các tình huống kiểm thử. Luật chi tiết tham chiếu [GAME.md](GAME.md); thiết kế dữ liệu tham chiếu [DATABASE.md](DATABASE.md).

## 1. Tổng quan

Fruit Battle Online là trò chơi đối kháng trực tuyến dành cho hai người. Mỗi người điều khiển một chiếc giỏ trên sân riêng để hứng đúng loại hoa quả mục tiêu, tránh quả sai và bom, đồng thời tích lũy năng lượng để tấn công đối thủ bằng kỹ năng.

Mỗi trận có 3 giây đếm ngược và 60 giây thi đấu. Khi hết thời gian, người có điểm cao hơn thắng; hai người bằng điểm thì hòa. Điểm có thể âm.

Game có một chế độ thi đấu 1v1 và sử dụng ghép trận tự động theo thứ tự vào hàng đợi. Hiện tại không có tạo phòng, mời bạn bè, đấu với máy, ghép theo trình độ, cửa hàng hoặc vật phẩm mua bán.

## 2. Tác nhân và phạm vi quyền

| Tác nhân | Nghiệp vụ |
|---|---|
| Khách chưa đăng nhập | Đăng ký và đăng nhập tài khoản. |
| Người chơi | Quản lý hồ sơ, tìm/hủy tìm trận, thi đấu, kết nối lại, xem kết quả, lịch sử và bảng xếp hạng, đăng xuất. |
| Quản trị viên | Xem danh sách tài khoản, khóa/mở khóa người chơi, xem thông tin trận đấu và thống kê hoạt động. |
| Hệ thống | Xác thực, quản lý phiên, ghép trận, điều khiển trận đấu, xác định kết quả và lưu dữ liệu. |

Chức năng quản trị chỉ được thực hiện bằng tài khoản có quyền ADMIN. Tài khoản đăng ký thông thường có quyền PLAYER; người dùng không tự chọn quyền quản trị khi đăng ký.

## 3. Luồng nghiệp vụ tổng thể

1. Người dùng mở game và đăng ký hoặc đăng nhập.
2. Sau khi xác thực thành công, hệ thống đưa người dùng vào sảnh chính; nếu có trận đang diễn ra thì khôi phục trận đó.
3. Người chơi chọn **Tìm trận** và được đưa vào hàng đợi.
4. Khi có hai tài khoản hợp lệ, hệ thống ghép thành một trận và thông báo đối thủ.
5. Hai người nhận cùng mục tiêu ban đầu và đếm ngược 3 giây.
6. Trong 60 giây thi đấu, người chơi di chuyển giỏ, hứng hoa quả và sử dụng kỹ năng.
7. Hệ thống tính điểm, cập nhật combo, năng lượng và hiệu ứng của hai sân.
8. Khi hết giờ hoặc phát sinh việc xử thua do khóa tài khoản, hệ thống kết thúc trận.
9. Hệ thống lưu kết quả, cập nhật thống kê và hiển thị màn hình kết quả.
10. Người chơi chọn **Chơi tiếp** để tìm trận mới hoặc trở về sảnh.

## 4. Quản lý tài khoản và hồ sơ

### NV-01. Đăng ký

**Mục đích:** Tạo tài khoản để sử dụng các chức năng của game.

**Điều kiện:** Người dùng chưa đăng nhập và kết nối được đến máy chủ.

**Dữ liệu nhập:** Tên tài khoản, mật khẩu và tên hiển thị.

**Quy tắc:**

- Tên tài khoản gồm 3–24 ký tự, chỉ chấp nhận chữ cái Latin không dấu, chữ số và dấu gạch dưới `_`.
- Tên tài khoản là duy nhất, không phân biệt chữ hoa/chữ thường. Ví dụ, `Anh1` và `anh1` không được đăng ký thành hai tài khoản.
- Mật khẩu dài 6–128 ký tự.
- Tên hiển thị dài 2–24 ký tự theo kiểm tra của ứng dụng, cho phép tiếng Việt và không chứa ký tự điều khiển.
- Nếu không nhập tên hiển thị, hệ thống sử dụng tên tài khoản.
- Tài khoản mới có quyền PLAYER, chưa bị khóa và có các chỉ số thắng/thua/hòa/tổng điểm bằng 0.

**Luồng chính:** Người dùng gửi thông tin → hệ thống kiểm tra → tạo và lưu tài khoản → đăng nhập vào tài khoản vừa tạo.

**Trường hợp không thành công:** Dữ liệu không hợp lệ, tên tài khoản đã tồn tại hoặc không lưu được dữ liệu. Hệ thống thông báo lỗi; đăng ký không được xem là thành công khi chưa lưu được tài khoản.

### NV-02. Đăng nhập và quản lý phiên

**Điều kiện:** Có tài khoản hợp lệ, chưa bị khóa.

**Luồng chính:**

1. Người dùng nhập tên tài khoản và mật khẩu.
2. Hệ thống kiểm tra thông tin xác thực và trạng thái tài khoản.
3. Hệ thống kiểm tra tài khoản có đang được sử dụng trên một kết nối khác hay không.
4. Nếu hợp lệ, hệ thống tạo phiên đăng nhập và trả thông tin hồ sơ.
5. Nếu tài khoản có trận chưa kết thúc, hệ thống đưa người chơi trở lại trận hiện tại.

**Quy tắc:** Mỗi tài khoản chỉ có một phiên đăng nhập đang hoạt động. Không cho phép đăng nhập đồng thời trên hai kết nối; người chơi có thể đăng nhập lại khi kết nối trước đã được hệ thống xác định là mất.

**Trường hợp không thành công:** Sai thông tin xác thực, tài khoản bị khóa, tài khoản đang được sử dụng hoặc thử xác thực quá nhiều lần trên cùng kết nối. Hệ thống trả thông báo phù hợp.

### NV-03. Xem và cập nhật hồ sơ

Người dùng đã đăng nhập được xem thông tin tài khoản, tên hiển thị, số trận thắng/thua/hòa và tổng điểm.

Người dùng có thể đổi tên hiển thị, chọn một trong hai avatar nhà vườn/cô làm vườn và đổi mật khẩu. Avatar được lưu theo tài khoản, hiển thị lại khi đăng nhập và khi thi đấu. Đổi mật khẩu yêu cầu nhập đúng mật khẩu hiện tại và mật khẩu mới hợp lệ. Tên đăng nhập không thay đổi qua chức năng hồ sơ.

Chỉ thông báo cập nhật thành công sau khi lưu được dữ liệu. Nếu lưu thất bại, hệ thống giữ thông tin trước khi chỉnh sửa. Thay đổi tên hiện tại không sửa lại tên đã lưu trong kết quả những trận trước.

### NV-04. Đăng xuất

Hệ thống kết thúc phiên đăng nhập, xóa trạng thái đăng nhập trên client và đưa người dùng về màn hình đăng nhập.

Nếu đang chờ ghép trận, tài khoản được xóa khỏi hàng đợi. Nếu đã ghép hoặc đang thi đấu, xử lý tương tự mất kết nối ở giai đoạn tương ứng tại mục 8; đăng xuất giữa trận không tự động được xem là nhận thua.

## 5. Ghép trận tự động

### NV-05. Tìm trận

**Điều kiện:** Người chơi đã đăng nhập, còn kết nối và tài khoản không bị khóa.

**Luồng chính:**

1. Người chơi chọn **Tìm trận**.
2. Hệ thống đưa tài khoản vào cuối hàng đợi và hiển thị trạng thái đang tìm đối thủ.
3. Hệ thống lấy hai tài khoản hợp lệ theo thứ tự hàng đợi để tạo trận.
4. Hai người được xóa khỏi hàng đợi và gắn vào cùng một trận.
5. Hệ thống gửi thông tin đối thủ, mục tiêu và trạng thái đếm ngược.

**Quy tắc:**

- Một tài khoản không được xuất hiện lặp trong hàng đợi.
- Một tài khoản không được tham gia hai trận đồng thời.
- Tài khoản mất kết nối, bị khóa hoặc đã có trận không được lấy để ghép trận mới.
- Nếu người chơi đã có trận, yêu cầu tìm trận sẽ đưa họ trở lại trận đó thay vì tạo thêm trận.
- Chỉ có một người chờ thì tiếp tục chờ; chưa có giới hạn thời gian chờ hay cơ chế ghép với máy.

### NV-06. Hủy tìm trận

Người chơi có thể hủy khi vẫn đang ở hàng đợi. Hệ thống xóa tài khoản khỏi hàng đợi và trả về trạng thái sảnh.

Sau khi đã ghép thành công, yêu cầu hủy tìm trận không còn hợp lệ. Nếu hủy và ghép xảy ra gần nhau, trạng thái đã được máy chủ xác nhận quyết định kết quả xử lý.

## 6. Thi đấu

### NV-07. Khởi tạo trận

Mỗi trận có một mã duy nhất và đúng hai người chơi khác nhau. Hai người có sân riêng, cùng loại hoa quả mục tiêu và cùng lịch sinh vật thể cơ bản, gồm loại vật thể và vị trí xuất hiện. Vật thể sinh thêm do kỹ năng chỉ tác động đến sân của người bị tấn công.

Khi bắt đầu, mỗi người có 0 điểm, 0 combo, 0 năng lượng và chưa sử dụng kỹ năng. Hệ thống đếm ngược 3 giây trước khi tính 60 giây thi đấu. Không được sử dụng kỹ năng trong thời gian đếm ngược.

### NV-08. Di chuyển và hứng vật thể

Người chơi dùng phím **← / →** để di chuyển giỏ theo chiều ngang, trong giới hạn sân. Hệ thống xác định việc hứng dựa trên vị trí giỏ và vật thể; người chơi không tự gửi điểm hoặc tự xác nhận đã hứng.

Trên mỗi sân, mỗi vật thể được xử lý hứng tối đa một lần. Vật thể rơi khỏi sân được loại bỏ.

| Tình huống | Thay đổi điểm | Combo | Năng lượng |
|---|---:|---|---|
| Hứng đúng quả mục tiêu | +10 | Tăng 1 | +10, tối đa 100 |
| Hứng sai loại quả | −5 | Về 0 | Không đổi |
| Hứng bom | −10 | Về 0 | Không đổi |
| Bỏ lỡ vật thể | 0 | Không đổi | Không đổi |
| Combo đạt bội số của 5 | Thưởng thêm +20 | Giữ chuỗi hiện tại | Không có phần thưởng năng lượng bổ sung |

Điểm không bị giới hạn ở 0. Bỏ lỡ một quả không làm mất combo; hứng sai hoặc hứng bom mới làm đứt chuỗi.

**Ví dụ:** Hứng đúng liên tiếp 5 quả được 50 điểm cơ bản và 20 điểm combo, tổng 70 điểm. Hứng đúng liên tiếp 10 quả được 100 điểm cơ bản và 40 điểm combo, tổng 140 điểm.

### NV-09. Đổi mục tiêu và tăng độ khó

Mục tiêu được chọn trong ba loại: táo, chuối và nho. Hai người luôn có cùng mục tiêu tại cùng thời điểm.

- Hệ thống chọn mục tiêu ban đầu khi tạo trận.
- Đổi mục tiêu tại các mốc 15, 30 và 45 giây thi đấu.
- Mục tiêu mới được chọn ngẫu nhiên và khác mục tiêu ngay trước đó.
- Vật thể đang rơi không bị xóa khi đổi mục tiêu. Hứng đúng/sai được tính theo mục tiêu **tại thời điểm hứng**, không theo lúc vật thể xuất hiện.

| Giai đoạn thi đấu | Tốc độ rơi cơ bản | Khoảng cách giữa các lần sinh vật thể cơ bản |
|---|---|---|
| Từ 0 đến trước 20 giây | Chậm | 1,2 giây |
| Từ 20 đến trước 40 giây | Trung bình | 0,95 giây |
| Từ 40 đến hết 60 giây | Nhanh | 0,7 giây |

### NV-10. Sử dụng kỹ năng

| Kỹ năng | Phím | Chi phí | Tác động |
|---|---|---:|---|
| Gửi bom | `1` | 30 năng lượng | Cảnh báo khoảng 0,65 giây rồi tạo một quả bom ở vị trí giỏ đối thủ khi kích hoạt. Đối thủ có thể di chuyển để né. |
| Làm chậm | `2` | 50 năng lượng | Giảm 40% tốc độ di chuyển giỏ đối thủ trong 3 giây. |
| Giỏ nam châm | `3` | 90 năng lượng | Trong 5 giây, nam châm gắn trên giỏ kéo nhẹ quả mục tiêu đã ở trong bán kính 120 về tâm giỏ. Người chơi vẫn tự di chuyển để hứng; vùng hứng giữ nguyên 100, bom và quả sai không bị hút, tốc độ rơi không đổi. |

Điều kiện sử dụng:

- Trận đang thi đấu, chưa hết giờ và chưa kết thúc.
- Người dùng có đủ năng lượng.
- Hết thời gian hồi chiêu chung 5 giây và kỹ năng trước của người dùng đã hết hiệu lực.
- Với kỹ năng 1/2, đối thủ không đang chịu một hiệu ứng tấn công khác. Kỹ năng 3 tác động lên bản thân, dùng được cả khi đang chịu tấn công.

Khi yêu cầu hợp lệ, hệ thống trừ năng lượng đúng một lần, tăng bộ đếm sử dụng kỹ năng, kích hoạt hiệu ứng và thông báo cho hai người. Hiệu ứng không cộng dồn. Yêu cầu không hợp lệ bị từ chối, không trừ năng lượng và không tăng số lần sử dụng.

## 7. Kết thúc trận, kết quả và thống kê

### NV-11. Xác định kết quả

Trong trường hợp thông thường, trận kết thúc khi đủ 60 giây thi đấu:

- Điểm người A cao hơn người B: A thắng, B thua.
- Điểm người B cao hơn người A: B thắng, A thua.
- Hai người bằng điểm: cả hai được tính hòa.

Combo, năng lượng còn lại và số kỹ năng không được dùng để phân định khi bằng điểm. Không có hiệp phụ.

Ngoại lệ: Nếu tài khoản bị quản trị viên khóa khi trận đang thi đấu, người đó bị xử thua và đối thủ thắng ngay, không phụ thuộc điểm hiện tại. Trận ghi nhận lý do kết thúc do khóa tài khoản.

Mỗi trận chỉ được kết thúc và cập nhật thống kê một lần. Sau khi kết thúc, người chơi không được tiếp tục làm thay đổi điểm hay sử dụng kỹ năng của trận đó.

### NV-12. Ghi nhận kết quả

Thông tin cần lưu:

- Mã trận, thời điểm ghép, thời điểm kết thúc và thời lượng thi đấu nếu có.
- Người thắng hoặc kết quả hòa, lý do kết thúc.
- Hai người tham gia và tên của họ tại lúc ghi kết quả.
- Điểm, số quả hứng đúng/sai, số bom hứng phải, combo cao nhất và số lần sử dụng kỹ năng của từng người.

Thống kê tài khoản được cập nhật như sau:

| Kết quả cá nhân | Cập nhật |
|---|---|
| Thắng | Số trận thắng tăng 1 |
| Thua | Số trận thua tăng 1 |
| Hòa | Số trận hòa tăng 1 |
| Mọi trận đã kết thúc hợp lệ | Tổng điểm cộng thêm điểm cá nhân trong trận, kể cả điểm âm |

Kết quả trận, hai dòng thống kê người tham gia và thống kê tổng của hai tài khoản được ghi trong cùng một transaction. Nếu một bước lỗi, lần ghi đó phải được rollback.

Nếu chưa lưu được kết quả, người chơi vẫn nhận kết quả kèm trạng thái chưa lưu; hệ thống giữ kết quả để thử lưu lại theo chu kỳ khoảng 5 giây khi máy chủ còn chạy. Thử lưu lại không được tạo trận trùng hay cộng thống kê hai lần. Không thông báo đã lưu khi dữ liệu chưa được xác nhận ghi thành công.

### NV-13. Xem kết quả và chơi tiếp

Màn hình kết quả hiển thị thắng/thua/hòa, điểm hai người và các chỉ số thi đấu. Người chơi có thể chọn **Chơi tiếp** để vào hàng đợi của một trận mới hoặc **Về trang chủ** để về sảnh.

Chơi tiếp không bảo đảm gặp lại đối thủ cũ; lần ghép mới tuân theo hàng đợi hiện tại.

## 8. Mất kết nối và kết nối lại

| Thời điểm xảy ra | Xử lý nghiệp vụ |
|---|---|
| Đang ở hàng đợi | Xóa tài khoản khỏi hàng đợi. |
| Đã ghép, còn đếm ngược | Hủy lượt ghép; người còn kết nối được đưa lại vào hàng đợi và có thể được ghép đối thủ mới. Không tính kết quả hay thống kê cho lượt bị hủy. |
| Đang thi đấu | Trận tiếp tục chạy, không dừng đồng hồ và không xử thua chỉ vì mất kết nối. Giỏ của người mất kết nối ngừng di chuyển; diễn biến sân vẫn do hệ thống xử lý. |
| Đăng nhập lại khi trận chưa kết thúc | Trở lại đúng trận, nhận thời gian còn lại, điểm, combo, năng lượng, vị trí, vật thể và các hiệu ứng hiện tại. Không đặt lại trận về ban đầu. |
| Đăng nhập lại sau khi trận đã kết thúc | Nhận kết quả đang chờ nếu còn; có thể xem lịch sử hoặc yêu cầu khôi phục kết quả trận gần nhất đã lưu. |

Năng lượng và thời gian hồi chiêu không được làm mới khi kết nối lại. Nếu cả hai cùng mất kết nối giữa trận, trận vẫn chạy đến khi kết thúc theo đồng hồ máy chủ.

**Giới hạn hiện tại:** Khôi phục một trận đang diễn ra yêu cầu tiến trình máy chủ đó vẫn hoạt động. Trạng thái thi đấu và hàng đợi đang ở bộ nhớ; chưa có nghiệp vụ tiếp tục trận dở sau khi máy chủ khởi động lại. Kết quả chưa lưu cũng không được bảo đảm còn sau khi tiến trình bị dừng. Kết quả đã lưu có thể đọc lại sau khi khởi động lại máy chủ.

## 9. Lịch sử và bảng xếp hạng

### NV-14. Xem lịch sử thi đấu

Người dùng đã đăng nhập xem tối đa 100 trận gần nhất của chính mình, theo thứ tự mới nhất trước. Mỗi dòng gồm thời điểm, đối thủ, điểm hai bên, kết quả cá nhân và các chỉ số thi đấu.

Lịch sử lấy tên đối thủ đã lưu trong kết quả, không thay bằng tên hồ sơ mới. Lượt ghép bị hủy trước khi bắt đầu không xuất hiện trong lịch sử.

### NV-15. Xem bảng xếp hạng

Bảng xếp hạng hiển thị tối đa 100 tài khoản người chơi đang không bị khóa. Tài khoản quản trị không tham gia bảng xếp hạng.

Thứ tự xếp hạng:

1. Số trận thắng giảm dần.
2. Nếu bằng số trận thắng, tổng điểm giảm dần.
3. Nếu tiếp tục bằng nhau, ID tài khoản tăng dần để bảo đảm thứ tự ổn định.

Chưa có điểm Elo, bậc hạng, mùa giải hoặc phần thưởng xếp hạng. Mở khóa tài khoản cho phép tài khoản đó xuất hiện lại theo thống kê đang có.

## 10. Quản trị

### NV-16. Xem tài khoản và tình trạng hệ thống

Quản trị viên được xem danh sách tài khoản, quyền, trạng thái khóa và thống kê; xem các trận gần đây và thông tin hoạt động như số tài khoản trực tuyến, người đang chờ, trận đang chạy, tổng trận đã ghi nhận và thời gian máy chủ hoạt động.

Danh sách tài khoản và trận hỗ trợ phân trang. Không hiển thị mật khẩu, salt hay hash mật khẩu trong dữ liệu hồ sơ/quản trị gửi đến giao diện.

Quản trị viên tìm tài khoản theo ID, tên đăng nhập hoặc tên hiển thị; tìm trận theo mã trận hoặc tên người tham gia. Tìm kiếm không phân biệt hoa/thường và dấu tiếng Việt, áp dụng trước khi phân trang trên toàn bộ dữ liệu. Mỗi danh sách giữ bộ lọc riêng; đổi từ khóa trở về trang đầu, làm mới và chuyển trang giữ bộ lọc đang áp dụng. Thống kê hệ thống vẫn thể hiện tổng toàn hệ thống.

### NV-17. Khóa và mở khóa tài khoản

**Điều kiện:** Người thực hiện có quyền ADMIN; tài khoản đích tồn tại và không phải tài khoản ADMIN.

**Luồng chính:** Quản trị viên chọn tài khoản → yêu cầu khóa hoặc mở khóa → hệ thống lưu trạng thái → thông báo kết quả.

Khi khóa thành công:

- Từ chối các lần đăng nhập tiếp theo của tài khoản đó.
- Xóa khỏi hàng đợi nếu đang tìm trận.
- Kết thúc phiên đang hoạt động và thông báo lý do.
- Nếu còn đếm ngược, xử lý hủy ghép theo mục 8.
- Nếu đang thi đấu, xử thua tài khoản bị khóa và kết thúc trận.
- Loại khỏi bảng xếp hạng; giữ nguyên tài khoản, thống kê và lịch sử.

Mở khóa cho phép đăng nhập và sử dụng game trở lại; không xóa kết quả đã ghi nhận, không tự đăng nhập lại và không khôi phục lượt ghép cũ. Nếu không lưu được trạng thái khóa/mở khóa, thao tác không được xem là thành công.

Hiện chưa có chức năng quản trị xóa tài khoản, sửa điểm trận, sửa kết quả hoặc cấp quyền ADMIN từ giao diện.

## 11. Trạng thái nghiệp vụ chính

| Trạng thái | Sự kiện chuyển trạng thái | Trạng thái tiếp theo |
|---|---|---|
| Chưa đăng nhập | Đăng nhập/đăng ký thành công | Ở sảnh, hoặc khôi phục trận hiện có |
| Ở sảnh | Tìm trận | Chờ ghép |
| Chờ ghép | Hủy tìm | Ở sảnh |
| Chờ ghép | Ghép đủ hai người | Đếm ngược |
| Đếm ngược | Hết 3 giây | Đang thi đấu |
| Đếm ngược | Một người mất kết nối | Hủy ghép; người còn lại trở về chờ ghép |
| Đang thi đấu | Một người mất kết nối | Trận vẫn thi đấu; người đó chờ kết nối lại |
| Đang thi đấu | Hết giờ hoặc khóa tài khoản | Kết thúc và ghi nhận kết quả |
| Kết quả | Chơi tiếp | Chờ ghép |
| Kết quả | Về trang chủ | Ở sảnh |
| Có phiên đăng nhập | Đăng xuất | Chưa đăng nhập; trận/hàng đợi xử lý theo giai đoạn |

## 12. Nguyên tắc dữ liệu

Hệ thống lưu bền vững dữ liệu trong đúng bốn bảng nghiệp vụ: accounts, player_stats, matches và match_players. Thông tin xác thực nằm cùng tài khoản; không có bảng xác thực hay bảng phiên bản riêng. Kết quả dùng cấu trúc quan hệ gồm một bản ghi trận và hai bản ghi người tham gia. Trận hòa không tham chiếu đến một tài khoản thắng giả.

Tên đăng nhập không trùng, người tham gia phải là tài khoản tồn tại, một người không chiếm hai vị trí trong cùng trận và người thắng phải thuộc hai người tham gia. Những ràng buộc liên quan toàn bộ trận được kiểm tra trước khi ghi kết quả.

Mật khẩu được lưu bằng hash có salt riêng. Client chỉ gửi yêu cầu thao tác; máy chủ quyết định việc ghép trận, va chạm, điểm, năng lượng, thời gian, kỹ năng và kết quả.

Định nghĩa bảng, khóa, chỉ mục và cơ chế chuyển dữ liệu nằm trong [DATABASE.md](DATABASE.md). Nghiệp vụ xem bảng xếp hạng và lịch sử được lấy từ tài khoản/thống kê/kết quả, không cần tạo thêm bảng xếp hạng hoặc bảng lịch sử riêng.

## 13. Các tình huống cần nghiệm thu

| Mã | Tình huống | Kết quả mong đợi |
|---|---|---|
| NT-01 | Đăng ký tên trùng nhưng khác hoa/thường | Bị từ chối, không tạo thêm tài khoản. |
| NT-02 | Hai kết nối đăng nhập cùng tài khoản | Chỉ một phiên đang hoạt động được chấp nhận. |
| NT-03 | Bấm tìm trận nhiều lần | Chỉ có một vị trí trong hàng đợi và tối đa một trận. |
| NT-04 | Hủy sau khi đã ghép | Từ chối hủy tìm trận; giữ trạng thái trận hợp lệ. |
| NT-05 | Hứng đúng liên tiếp 5 quả | Có 70 điểm, combo 5 và 50 năng lượng nếu chưa dùng kỹ năng. |
| NT-06 | Bỏ lỡ quả khi đang có combo | Điểm, combo và năng lượng không đổi do lần bỏ lỡ đó. |
| NT-07 | Hứng sai hoặc bom khi điểm đang bằng 0 | Điểm được phép xuống âm; combo về 0. |
| NT-08 | Mục tiêu đổi khi quả còn đang rơi | Tính đúng/sai theo mục tiêu tại lúc hứng. |
| NT-09 | Dùng kỹ năng khi thiếu năng lượng hoặc còn hồi chiêu | Không kích hoạt và không trừ năng lượng. |
| NT-10 | Hai người bằng điểm khi hết giờ | Ghi hòa cho cả hai, không dùng chỉ số phụ phân thắng thua. |
| NT-11 | Một người mất kết nối khi đang đếm ngược | Hủy ghép, không cộng thống kê; người còn lại tiếp tục tìm trận. |
| NT-12 | Một người kết nối lại giữa trận | Khôi phục trận hiện tại, không đặt lại thời gian hoặc năng lượng. |
| NT-13 | Khóa người chơi trong lúc thi đấu | Kết thúc phiên, xử thua người bị khóa và ghi kết quả tương ứng. |
| NT-14 | Lưu kết quả lỗi rồi thử lại | Không có bản ghi trận trùng, không cộng thống kê hai lần. |
| NT-15 | Đổi tên sau một trận đã lưu | Hồ sơ đổi tên, lịch sử trận trước giữ snapshot tên cũ. |
| NT-16 | Khởi động lại máy chủ sau khi lưu trận | Đọc lại đúng tài khoản, thống kê và kết quả đã lưu. |
