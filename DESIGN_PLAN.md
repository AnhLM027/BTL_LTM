# Kế hoạch nâng cấp hình ảnh, hiệu ứng và âm thanh

Ghi chú cấu trúc: kế hoạch này được viết khi dự án còn ba file Java. Mã hiện tại đã tách `client`, `server`, `common` theo tính năng; xem [README.md](README.md) và [FEATURE_REFACTOR.md](FEATURE_REFACTOR.md).

Ngày khảo sát: 28/10/2026. Kế hoạch đã được triển khai. Phần khảo sát dưới đây mô tả bản trước khi nâng cấp; thay đổi sau đó ghi ở mục 9.

## 1. Những điểm cần sửa trong bản hiện tại

Đối chiếu trực tiếp `Client.java`, `Server.java`, `pom.xml` và `run.ps1`:

- Menu chính dùng hero, slogan, nhiều card và thanh điều hướng giống ứng dụng dashboard. Phần hình ảnh chưa thể hiện rõ không khí một trò chơi đối kháng trong vườn quả.
- Segoe UI, Consolas, emoji và icon vẽ bằng ô vuông đang dùng chung. Cần một hệ chữ, icon và khung giao diện nhất quán.
- Đã có nhấp nháy điểm, pulse mục tiêu, shimmer năng lượng, nền sao và pulse nút tìm trận. Cần cải thiện các chuyển động này theo ngữ cảnh; không coi toàn bộ animation là chưa có.
- `receive()` chưa xử lý `CATCH_EVENT`, `TARGET_CHANGE`, `SCORE_UPDATE` riêng. Server đã phát các message này, nhưng client chủ yếu dùng snapshot `GAME_STATE`; đây là điểm móc tốt để bổ sung phản hồi theo sự kiện.
- Chưa thấy bộ phát âm thanh, file âm thanh hoặc resources hình ảnh/âm thanh trong mã nguồn đã khảo sát.
- Timeline tìm trận được tạo với chu kỳ vô hạn và gắn vào nút; chưa thấy phần dừng khi đổi màn hình. Cần quản lý vòng đời animation để tránh giữ màn hình cũ trong bộ nhớ.
- Nền sao đang cập nhật với bước cố định `0.016`; nên dùng thời gian thực giữa các frame để tốc độ không phụ thuộc FPS.
- Script chạy hiện biên dịch Java nhưng chưa có bước sao chép resources vào classpath.

## 2. Skill phù hợp đã tìm được

Danh mục curated của `openai/skills` vừa liệt kê không có skill chuyên game art/game audio. Các nguồn dưới đây được đọc trực tiếp; chưa cài vào máy.

| Skill | Nguồn | Cách sử dụng đề xuất |
|---|---|---|
| `game-art` | Community: [SKILL.md](https://github.com/sickn33/antigravity-awesome-skills/blob/main/skills/game-development/game-art/SKILL.md) | Tham khảo quy trình art direction, sprite và animation; phù hợp pixel art. |
| `game-audio` | Community: [SKILL.md](https://github.com/sickn33/antigravity-awesome-skills/blob/main/skills/game-development/game-audio/SKILL.md) | Lập danh mục âm thanh, cách chuyển nhạc và cân bằng các nhóm âm. Đây là hướng dẫn, không tự cung cấp file sound. |
| `game-ui-frontend` | OpenAI: [SKILL.md](https://github.com/openai/plugins/blob/main/plugins/game-studio/skills/game-ui-frontend/SKILL.md) | Tham khảo bố cục HUD/menu và bảo vệ vùng chơi. Skill viết cho browser game; áp dụng nguyên tắc thiết kế vào JavaFX, không chuyển dự án sang web. |

Ưu tiên hai skill community đầu cho nội dung chuyên môn; dùng skill UI làm tài liệu tham khảo. Nếu cài ở bước triển khai, cần đọc toàn bộ nội dung và các tài liệu phụ thuộc, ghi lại phiên bản nguồn. Không cài hàng loạt các skill frontend không liên quan.

## 3. Hướng mỹ thuật đề xuất: Vườn đấu arcade

### Ngôn ngữ hình ảnh

- Pixel art 16-bit, màu gỗ, kem, xanh lá và màu trái cây; khoảng 16–24 màu chủ đạo là mục tiêu ban đầu.
- Có bộ sprite nhất quán: trái cây 16/32 px, giỏ, bom, icon kỹ năng, mảnh băng, tia tốc độ, bảng gỗ và huy hiệu kết quả.
- Dựng cảnh vườn có tiền cảnh, hàng cây, bầu trời; nền giảm tương phản để quả, bom và giỏ dễ nhận biết.
- Dùng một atlas sprite và một atlas UI nhỏ. Asset hình ảnh có thể tăng, số file Java chính vẫn giữ ba.
- Vẽ pixel trên lưới thống nhất, kiểm tra cách phóng để không bị nhòe; tọa độ xử lý của server vẫn là 800 × 600.
- Chữ pixel dùng có chọn lọc ở logo, số điểm và tiêu đề. Nội dung tiếng Việt cần bộ font có đủ dấu, kiểm tra thực tế trước khi chốt.
- [Pixelify Sans](https://github.com/google/fonts/blob/main/ofl/pixelifysans/METADATA.pb) là ứng viên cho phần tiêu đề Latin; metadata không liệt kê Vietnamese nên chưa chọn làm font duy nhất cho toàn UI.
- Bỏ emoji hệ thống khỏi HUD, thay bằng icon cùng bộ sprite.

### Thay đổi từng màn hình

| Màn hình | Bố cục mới |
|---|---|
| Đăng nhập/đăng ký | Bảng đăng nhập đặt trong cảnh vườn, logo gọn, thông tin máy chủ thu gọn; giảm lời quảng cáo. |
| Trang chủ | Giỏ/nhân vật ở giữa cảnh, nút “Tìm trận” là hành động chính; hồ sơ, lịch sử và xếp hạng thành các lựa chọn phụ gọn. |
| Tìm trận | Giỏ có động tác chờ, đồng hồ tìm và nút hủy rõ ràng; tránh phóng to/thu nhỏ toàn bộ giao diện liên tục. |
| Thi đấu | Hai sân là trọng tâm. Mục tiêu và thời gian dễ thấy; điểm, combo, năng lượng đặt sát sân tương ứng. Ba ô kỹ năng có icon, phím và hồi chiêu. |
| Kết quả | Giỏ/nhân vật ăn mừng hoặc buồn nhẹ, điểm và thống kê rõ; Chơi tiếp/Về trang chủ vẫn là hai lựa chọn trực tiếp. |
| Hồ sơ/lịch sử/admin | Giữ khả năng đọc bảng và thao tác bằng bàn phím; thay màu, khung, icon theo bộ chung, không thêm animation vào mọi ô dữ liệu. |

## 4. Hiệu ứng gắn với gameplay

Các thời lượng dưới đây là thông số mỹ thuật dự kiến, sẽ tinh chỉnh khi chơi thử. Không thay đổi thời lượng kỹ năng hoặc luật trong GAME.md.

| Sự kiện | Phản hồi hình ảnh | Phản hồi âm thanh |
|---|---|---|
| Hứng đúng | Giỏ nảy nhẹ, 6–10 hạt màu quả, số điểm nổi khoảng 0,5 giây | Pop/chime ngắn, có vài biến thể |
| Hứng sai | Số −5, giỏ lệch nhẹ rồi trở lại; phản hồi ngắn khoảng 0,15 giây | Thud thấp, khác rõ tiếng hứng đúng |
| Hứng bom | Sprite nổ 4–6 frame, khói và rung nhẹ riêng sân bị trúng | Explosion ngắn; hạ nhạc nền thoáng qua |
| Combo bội số 5 | Huy hiệu combo bật lên, điểm thưởng +20 rõ ràng | Chuỗi nốt tăng dần |
| Gửi bom | Vệt tấn công ngắn và cột cảnh báo tại vị trí server cung cấp | Cast + cảnh báo bom riêng biệt |
| Làm chậm | Mảnh băng quanh giỏ, icon thời gian hiệu lực | Ice cast; tiếng tan băng khi kết thúc |
| Giỏ nam châm | Biểu tượng nam châm gắn trên giỏ, quả mục tiêu gần giỏ chuyển hướng nhẹ và đếm ngược 5 giây | Cue kích hoạt/kết thúc ngắn |
| Đổi mục tiêu | Icon đổi bằng chuyển động ngắn, nhãn tên quả dễ đọc | Một cue thông báo nhẹ |
| Đếm ngược/bắt đầu | 3–2–1–Bắt đầu, mỗi số chỉ xuất hiện một lần | Tick/tick/tick/go |
| Còn 10 giây | Đồng hồ nổi bật hơn, cảnh báo có tiết chế | Cue 10 giây, các tick cuối không lấn SFX |
| Kết thúc | Dải kết quả và động tác thắng/thua/hòa | Jingle riêng theo kết quả |

Giới hạn: không rung cả cửa sổ, không che vùng giỏ bằng banner, không tạm dừng mô phỏng vì hiệu ứng. Có tùy chọn giảm chuyển động/rung. Tắt âm vẫn phải hiểu mọi cảnh báo quan trọng.

## 5. Nguồn âm thanh và danh mục cần chuẩn bị

Các nguồn dưới đây đã kiểm tra trang gốc và thông tin giấy phép; chưa tải hoặc nghe chọn từng file.

| Nguồn | Công dụng dự kiến | Thông tin nguồn |
|---|---|---|
| [Kenney Interface Sounds](https://kenney.nl/assets/interface-sounds) | Click, xác nhận, lỗi thao tác | Trang tác giả ghi CC0 |
| [Kenney Impact Sounds](https://kenney.nl/assets/impact-sounds) | Va chạm, hứng sai, phần thân tiếng nổ | Trang tác giả ghi CC0 |
| [Kenney Music Jingles](https://kenney.nl/assets/music-jingles) | Thắng, thua, hòa | Trang tác giả ghi CC0; là đoạn nhạc ngắn, không thay thế nhạc nền |
| [Two Simple Game Music Loops — qubodup](https://opengameart.org/content/two-simple-game-music-loops) | Ứng viên nhạc menu và thi đấu | Trang tác giả ghi CC0, có WAV/OGG; cần nghe để đánh giá hợp phong cách |
| [jsfxr / sfxr.me](https://github.com/chr15m/jsfxr) | Tạo SFX retro đồng bộ cho hứng quả, combo và kỹ năng | Công cụ tạo sound, không phải skill Codex hoặc phụ thuộc runtime Java |

Danh mục tối thiểu: click, confirm, deny, match-found, countdown, go, catch-correct (3 biến thể), catch-wrong, bomb-warning, bomb-hit, combo, freeze-cast/end, magnet-cast/end, target-change, time-warning, win/lose/draw; hai loop menu/game.

Quy trình chọn: nghe từng ứng viên trong ngữ cảnh game, cân âm lượng, cắt khoảng lặng, kiểm tra loop, xuất PCM WAV, ghi đường dẫn gốc/tác giả/giấy phép/chỉnh sửa trong `ASSET_CREDITS.md`. Ưu tiên SFX tổng hợp cùng phong cách thay vì ghép nhiều pack nghe rời rạc.

## 6. Cách tích hợp, giữ code gọn

1. Giữ JavaFX, TCP và ba file Java chính. Phần mới chủ yếu nằm trong `Client.java`, với các lớp lồng `Audio` và `Effect` có trách nhiệm rõ ràng.
2. Dùng `javax.sound.sampled.Clip` và PCM WAV cho bản đầu. [API Java Clip](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/sound/sampled/Clip.html) hỗ trợ tải sẵn và phát lặp; không cần thêm JavaFX Media cho phương án này.
3. Preload âm thanh ngoài luồng giao diện, tái sử dụng một nhóm clip giới hạn; không mở/giải mã file ở mỗi lần hứng quả. Nếu không có thiết bị âm thanh, game vẫn chạy.
4. Có Master/Music/SFX, nút mute và lưu tùy chọn. Nhạc nhỏ hơn cue gameplay; kiểm tra cân âm bằng nghe thật. Hạn chế số sound đồng thời, phát âm sân đối thủ nhỏ hơn âm của mình.
5. Xử lý `CATCH_EVENT` theo `matchId + playerId + objectId`; dùng `delta` và `combo` do server gửi để chọn phản hồi. Không tự tính điểm.
6. `SKILL_EFFECT`, `TARGET_CHANGE`, `MATCH_START`, `MATCH_RESULT` kích hoạt phản hồi một lần. `GAME_STATE` phục hồi trạng thái liên tục; không phát lại sound mỗi snapshot hoặc phát bù toàn bộ hiệu ứng khi rejoin.
7. Với đếm ngược, chỉ phát khi giá trị nguyên thực sự chuyển; rejoin không đọc lại các số đã qua. Hồi chiêu/kết thúc freeze và magnet dựa trên trạng thái server.
8. Giới hạn số particle, dùng thời gian giữa các frame, dọn effect/Timeline khi đổi màn hình hoặc thoát. Thử nội suy vị trí hiển thị giữa các snapshot để giảm giật, không chuyển xử lý va chạm sang client.
9. Đặt tài nguyên trong `src/main/resources/fruit/`: `sprites.png`, `ui.png`, `fonts/`, `sfx/`, `music/`. Cập nhật `run.ps1` để copy resources vào `target/classes`; Maven dùng cơ chế resources chuẩn. Cả hai đường chạy phải được kiểm tra.

## 7. Thứ tự triển khai và đầu ra

| Bước | Công việc | Đầu ra để đánh giá |
|---|---|---|
| 1 — Chốt phong cách | Bảng màu, chữ, giỏ/quả/bom mẫu; concept trang chủ và màn hình thi đấu | Hai bản thiết kế + một bảng asset mẫu, trước khi sửa hàng loạt màn hình |
| 2 — Làm một lát cắt hoàn chỉnh | Một sân theo art mới, hứng đúng/sai/bom với SFX, mute, ghép qua TCP thật | Chơi thử có cả hình và âm; xác nhận phản hồi dễ hiểu và hợp nhau |
| 3 — Hoàn thiện cảm giác chơi | Combo, ba kỹ năng, đổi mục tiêu, countdown, cảnh báo cuối trận, nội suy hiển thị | Một trận đủ 60 giây với toàn bộ cue, không che vật thể quan trọng |
| 4 — Đồng bộ toàn UI | Đăng nhập, trang chủ, bảng dữ liệu, admin, kết quả, nhạc theo trạng thái | Mọi màn hình cùng một hệ mỹ thuật; thao tác tài khoản vẫn đầy đủ |
| 5 — Kiểm chứng | Regression TCP/MySQL/UI, nghe thử, resize, rejoin, mute, thiếu audio device | Ảnh/clip trước–sau, báo cáo test, nguồn asset và README cập nhật |

Ưu tiên bước 1–2 trước: có một phần game nhìn và nghe đúng phong cách rồi mới mở rộng. Chưa đặt thời gian hoàn thành cố định khi chưa chọn xong asset và nghe thử.

## 8. Điều kiện nghiệm thu

- Bản mới có bố cục game rõ ràng, hạn chế card/slogan; icon, sprite, font cùng phong cách.
- Chữ tiếng Việt không lỗi dấu hoặc fallback lẫn lộn; HUD đọc được ở 1024×720 và 1280×820.
- Hứng đúng/sai/bom phân biệt ngay bằng hình và âm; combo và cả ba kỹ năng có phản hồi riêng.
- Sound phát đúng một lần mỗi sự kiện. Rejoin nhận snapshot không tạo chuỗi âm cũ; chuyển màn hình không để nhạc/Timeline cũ chạy chồng.
- Có Master/Music/SFX/mute, lưu được tùy chọn; giảm chuyển động vẫn giữ cảnh báo cần thiết.
- Kiểm tra FPS trước/sau trên cùng máy, mục tiêu gần 60 FPS ở cấu hình chạy thử; số particle và audio channel có giới hạn. Đây là mục tiêu kiểm tra, chưa phải kết quả đo.
- Chơi nhiều trận liên tiếp không tăng clip/Timeline tồn tại không giới hạn. Thiếu file hoặc thiết bị âm thanh không làm mất khả năng chơi.
- Luật 60 giây, điểm, kỹ năng, fairness, TCP, MySQL, ghép trận và reconnect vẫn qua các bộ kiểm tra hiện có.
- Cả Maven và `run.ps1` tải được asset khi chạy từ thư mục khác; nguồn và giấy phép của mọi asset được ghi lại.

Ghi chú lịch sử: lượt khảo sát ban đầu chỉ thêm tài liệu. Lượt triển khai tiếp theo đã tạo cảnh vườn, atlas, SFX/nhạc riêng và cập nhật UI; lựa chọn nguồn thực tế được ghi trong `ASSET_CREDITS.md`. Các skill khảo sát được dùng làm tài liệu tham khảo, không phải phụ thuộc runtime.

## 9. Chỉnh giao diện ngày 01/11/2026

- Đăng nhập: co/cắt nền đúng tỉ lệ, giỏ nằm trong khung và hình minh họa co theo chiều rộng cửa sổ.
- Sảnh chính: giữ bố cục hiện tại, tăng chiều cao hình minh họa và cắt ảnh theo khung, không kéo méo.
- Xếp hạng: ba cúp pixel vàng/bạc/đồng vẽ bằng JavaFX Canvas; tìm theo tên hiển thị/tài khoản trong top 100, hỗ trợ Enter và xóa lọc; giữ nguyên hạng khi lọc.
- Lịch sử và quản trị trận: ngày giờ địa phương dạng `dd/MM/yyyy HH:mm`.
- Hồ sơ: chọn hai chân dung có sẵn; lưu `accounts.avatar_id`, đồng bộ sảnh và màn hình thi đấu.
- Hướng dẫn: ba thẻ Bắt đầu / Ghi điểm / Kỹ năng, có hình giỏ, trái cây, bom và biểu tượng kỹ năng từ tài nguyên game.
- Trong trận, thanh thông báo dưới cùng chỉ hiện khi mất kết nối hoặc thao tác bị từ chối. Lỗi thao tác tự ẩn sau 3 giây; mất kết nối giữ đến khi kết nối lại. Thông tin bom, kỹ năng, mục tiêu và combo hiển thị trên sân; các màn hình ngoài trận vẫn giữ thông báo thao tác.

Code hiện được chia theo features ở client/server; tài liệu kế hoạch ban đầu ở mục 6 phản ánh cấu trúc trước khi refactor.
