# Client assets

Đây là thư mục asset runtime của client. Mọi đường dẫn trong source nên dùng
đường dẫn tương đối từ `Client/assets/` và dấu `/`.

## Cấu trúc chuẩn

| Thư mục | Nội dung |
| --- | --- |
| `backgrounds/` | Nền đăng nhập, đăng ký, sảnh và gameplay |
| `fruits/` | Sprite hoa quả |
| `baskets/` | Sprite giỏ |
| `bombs/` | Sprite bom trong Classic |
| `icons/` | Icon chức năng và trạng thái |
| `logo/` | Logo ứng dụng |
| `modes/` | Hình minh họa chế độ `mode-classic.png`, `mode-order.png` |
| `nutrition/` | Icon/asset dinh dưỡng |
| `results/` | Hình kết quả trận |
| `ui/waiting-room/` | Panel, avatar, nút và icon của phòng chờ |
| `fonts/` | Font đóng gói cùng client |
| `design/` | Tài liệu kích thước, layout và preview; không dùng trực tiếp khi chạy |
| `legacy/` | Asset cũ giữ lại để tham chiếu, không dùng trong UI mới |

## Quy ước đường dẫn

- Source mới dùng `AssetPaths` và `AssetLoader`/`FxAssets` để tải asset.
- Có thể ghi trong DB dạng `Client/assets/fruits/apple.png`; loader sẽ tự bỏ
  tiền tố `Client/assets/`.
- Các đường dẫn cũ của phòng chờ, `background/`, `bomb/` và tên mode cũ vẫn
  được normalize để hỗ trợ dữ liệu đã tồn tại, nhưng không tạo thêm code mới
  dùng các alias đó.
- Font UI chính là `fonts/FVF.ttf`; font pixel là
  `fonts/PixelifySans[wght].ttf`.

