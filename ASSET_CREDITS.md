# Tài nguyên Fruit Battle Online

## Bộ hình theo ảnh mẫu — 28/10/2026

Ảnh mẫu do người dùng cung cấp làm tham chiếu phong cách; không đóng gói ảnh chụp giao diện đó vào game. Bộ hình mới được tạo bằng **imagegen tích hợp**, không dùng CLI/API fallback. Các file gốc được chép nguyên vào dự án:

| File trong `src/main/resources/fruit/` | Kích thước | Vai trò |
|---|---|---|
| `orchard-reference.png` | 1536 × 1024, RGB | Cảnh vườn trời xanh, cây táo, núi và cối xay |
| `skin-frame.png` | 1254 × 1254, RGBA | Khung gỗ có lá/hoa, tâm trong suốt; dựng chín phần |
| `portraits-reference.png` | 1774 × 887, RGBA | Chân dung người chơi trái/phải |
| `items-reference-v2.png` | 1774 × 887, RGBA | Táo, chuối, nho, bom, giỏ, băng, tốc độ và cúp; bản đã sửa khoảng trống giữa các ô |
| `basket-frozen.png` | PNG RGBA, nền trong suốt | Giỏ phủ băng khi bị làm chậm; tạo bằng imagegen tích hợp ngày 01/11/2026, tham chiếu giỏ trong atlas hiện tại |

Toàn bộ prompt cuối: [REFERENCE_ASSET_PROMPTS.md](REFERENCE_ASSET_PROMPTS.md). Chữ, điểm số, đồng hồ, thanh năng lượng và thao tác là thành phần JavaFX trực tiếp. Các tài nguyên đời trước đang dùng để fallback được giữ lại; atlas nháp `items-reference.png` đã được dọn, runtime dùng `items-reference-v2.png`.

## Cảnh vườn

- File đóng gói: `src/main/resources/fruit/orchard.png`, 1536 × 1024.
- Tạo riêng cho dự án bằng công cụ **imagegen tích hợp** ngày 28/10/2026. Không dùng ảnh tham chiếu bên ngoài. Không dùng CLI/API fallback.
- Ảnh gốc được chép nguyên vào dự án; game hiển thị nó qua Canvas. Atlas icon và logic gameplay được xử lý riêng.
- Prompt cuối đã dùng:

> Create a production game environment asset for Fruit Battle Online, a charming competitive 16-bit fruit catching game. Wide landscape composition 3:2 ratio. Pure carefully authored pixel art with crisp nearest-neighbor pixel clusters, like a high quality 1990s handheld orchard game, limited 24 color palette, not an app interface. A sunlit orchard clearing in autumn: soft pale mint sky filling upper 55 percent with a few block-shaped cream clouds, distant low sage hills, rich rounded apple tree crowns along left and right sides bearing small coral apples, rustic wooden fence along lower third, warm ochre dirt path and grass at bottom, a tiny orchard stall and apple crates tucked to the far lower left, a windmill tiny on the distant right. Flat straight-on orthographic side view for a fruit catching playfield. Center upper and central lower area mostly uncluttered to support overlays and falling fruits. Layers have distinct chunky pixel silhouettes, warm cream highlights, dark forest green shadows, terracotta and honey wood accents. Maintain clear flat pixel shapes and no anti-aliased painting. NO text, NO lettering, NO logos, NO characters, NO basket, NO falling fruit in the central playfield, NO UI, NO panels, NO gradient or glow. The image itself is the finished background that will fill a canvas behind a game UI.

## Sprite và khung giao diện đời đầu — fallback

- `sprites.png`: chín ô 32 × 32 cho bộ icon dự phòng; biểu tượng nam châm hiện được vẽ trực tiếp bằng Canvas.
- `ui.png`: khung gỗ 32 × 32, hiển thị theo chín phần để giữ viền sắc nét.
- Nguồn là các sprite ô ký tự do dự án tạo trong `client/screens/GameArt.java`, mở rộng bằng icon cùng phong cách. Không lấy sprite từ game khác.
- Xuất atlas bằng `python tools/export_atlas.py` (Pillow chỉ cần khi tái tạo atlas). Python không phải phụ thuộc runtime của game.
- Bản đời đầu vẽ giỏ, chân dung và cúp bằng các hình Canvas. Giao diện hiện tại ưu tiên bộ ảnh theo mẫu ở trên; particle và hiệu ứng vẫn vẽ trực tiếp bằng Canvas.
- Biểu tượng nam châm của kỹ năng 3 được vẽ trực tiếp bằng Canvas trong `client/shared/ui/GameArt.java`, không dùng ảnh bên ngoài. Giỏ khi kích hoạt hiển thị rộng thêm 40% và có quầng sáng.

## Âm thanh

- 22 SFX và hai loop nhạc được tổng hợp riêng bằng `tools/build_audio.py`, không dùng sample hoặc bài nhạc của bên thứ ba.
- `magnet.wav` và `magnet-end.wav` tái sử dụng hai cue tổng hợp của kỹ năng 3 trước đây, đã đổi tên đồng bộ trong code, script và danh sách SHA-256.
- Các giai điệu, nhịp và envelope có nguồn đầy đủ trong script. Tái tạo bằng `python tools/build_audio.py`, chỉ dùng thư viện chuẩn Python.
- Định dạng: PCM WAV mono 16-bit, 22050 Hz. Nền menu 108 BPM, nền thi đấu 144 BPM.
- SHA-256 từng file nằm trong `src/main/resources/fruit/audio-sha256.txt`.
- Kenney/jsfxr/qubodup là các ứng viên khảo sát trong kế hoạch; bản triển khai này **không đóng gói** file âm thanh từ các nguồn đó.

## Font

| Font | Nguồn tác giả | Giấy phép đi kèm |
|---|---|---|
| Noto Sans Regular / Bold / Black | [notofonts/noto-fonts](https://github.com/notofonts/noto-fonts/tree/main/hinted/ttf/NotoSans) | `src/main/resources/fruit/fonts/NotoSans-OFL.txt` — SIL OFL 1.1 |
| Pixelify Sans | [Google Fonts / Stefie Justprince](https://github.com/google/fonts/tree/main/ofl/pixelifysans) | `src/main/resources/fruit/fonts/PixelifySans-OFL.txt` — SIL OFL 1.1 |

Noto Sans phục vụ nội dung tiếng Việt. Pixelify Sans dùng cho logo Latin và số HUD; không ép font pixel lên các chữ thiếu dấu.

## Hướng thiết kế

- [game-art](https://github.com/sickn33/antigravity-awesome-skills/blob/main/skills/game-development/game-art/SKILL.md): tham khảo art direction và animation.
- [game-audio](https://github.com/sickn33/antigravity-awesome-skills/blob/main/skills/game-development/game-audio/SKILL.md): tham khảo phân nhóm âm thanh và cân âm.
- [game-ui-frontend](https://github.com/openai/plugins/blob/main/plugins/game-studio/skills/game-ui-frontend/SKILL.md): tham khảo HUD/menu; triển khai vẫn là JavaFX.

Đây là các tài liệu tham khảo đã khảo sát, không phải plugin hay thư viện runtime đã cài vào game.
