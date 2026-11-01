# Vườn đấu arcade — giao diện theo ảnh mẫu

- Bối cảnh: một cuộc thi thu hoạch trong vườn táo, nhìn ngang, phong cách arcade 16-bit.
- Sảnh chính dùng lại bố cục phiên bản đầu: lời chào phía trên; cột trái có giới thiệu, cảnh vườn nhỏ và nút Tìm trận; cột phải có hồ sơ, thống kê và mẹo combo. Hàng ba thẻ Di chuyển/Hứng đúng/Tung chiêu đã được bỏ theo yêu cầu. Màu sắc, khung gỗ và hình ảnh dùng bộ hiện tại.
- Màn hình thi đấu đặt hai sân cùng kích thước bên cạnh nhau, HUD ở ngoài vùng rơi, cảnh báo nằm ở sân bị tác động.
- Hình cảnh hiện dùng: `src/main/resources/fruit/orchard-reference.png`, tạo bằng imagegen tích hợp từ phong cách ảnh mẫu người dùng; bầu trời xanh, cây táo, núi và cối xay. Prompt và nguồn font trong `ASSET_CREDITS.md`.
- Gỗ vát cạnh có vân, lá và hoa ở góc; bảng giấy kem, chân dung nông dân đội nón, đồng hồ vàng giữa hai bảng điểm. Hai sân tận dụng chiều ngang, thanh kỹ năng và hướng dẫn nằm bên dưới.
- Chân dung và tám sprite lấy từ atlas có alpha, giữ đúng tỷ lệ hình; giới hạn tọa độ thế giới vẫn do server quản lý trên 800 × 600.
- Logo/số: Pixelify Sans. Nội dung tiếng Việt: Noto Sans Regular/Bold/Black. Icon dùng atlas pixel, không dùng emoji của hệ điều hành.

| Màu chính | Giá trị | Vai trò |
|---|---|---|
| Giấy kem | `#ffedc4` | Form, HUD và nền chữ |
| Gỗ ấm | `#8f4c28` | Nền ván gỗ |
| Mực nâu | `#42230f` | Nội dung chính |
| Xanh thu hoạch | `#285b26` | Người chơi, nút chính |
| Gỗ | `#925326` | Viền/bảng |
| Đỏ quả chín | `#b63129` | Đối thủ, bom, trừ điểm |
| Xanh băng | `#17608a` | Làm chậm, hiệu ứng băng |
| Chữ phụ | `#775334` | Thông tin phụ |

Các sắc phụ của cảnh/sprite phục vụ chiều sâu và hình dáng; không ép toàn PNG về đúng tám màu UI.

Hai màn hình để đối chiếu thiết kế:

- [Trang chủ](target/ui-home.png)
- [Gameplay](target/ui-game.png)
- [Cửa sổ nhỏ](target/ui-home-1024.png)
- [Atlas sprite](src/main/resources/fruit/items-reference-v2.png)
- [Chân dung](src/main/resources/fruit/portraits-reference.png)
- [Khung gỗ](src/main/resources/fruit/skin-frame.png)
- [Prompt tạo ảnh](REFERENCE_ASSET_PROMPTS.md)

Ảnh benchmark của đợt nâng cấp âm thanh/hiệu ứng trước đây:

- [Trước](target/perf-baseline.png)
- [Sau](target/perf-redesign.png)
