# Cấu trúc theo tính năng – Fruit Battle Online

Cấu trúc hiện tại gồm 51 file Java sản phẩm. Console nằm trong `Server.java`; mã chuyển đổi database cũ đã được dọn sau khi chuyển xong. Bộ kiểm thử nằm riêng trong `src/test/java`. Tệp SQL backup trong `data/backups` được giữ nguyên.

Cập nhật ngày 31/10/2026. Hai nhánh `client` và `server` được giữ riêng. `application` điều phối các tính năng; `features` chứa xử lý nghiệp vụ và giao diện tương ứng; `infrastructure` chứa TCP và lưu trữ; `common` chỉ chứa giao thức/dữ liệu dùng chung.

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

## Trách nhiệm

- `GameServer` giữ vòng lặp engine, dispatcher và tài nguyên kết nối; gọi service của tính năng để xác thực, quản trị, ghép trận và truy vấn thống kê.
- `AccountService` xử lý đăng ký/đăng nhập, hồ sơ và tạo admin; `AccountRepository` ánh xạ accounts/player_stats.
- `MatchmakingService` xử lý hàng đợi, ghép/hủy, khôi phục trận và mất kết nối.
- `Match` giữ luật thi đấu; `MatchRepository` ánh xạ matches/match_players và kiểm tra kết quả hai người.
- `AdminService` xử lý truy vấn quản trị và khóa/mở khóa; `StatisticsService` xử lý lịch sử và thứ tự bảng xếp hạng.
- `Store` giữ cache của engine, biên transaction SQL và chế độ JSONL cục bộ. Không còn SqlStore chung cho mọi nghiệp vụ.
- `ClientController` tiếp nhận message và điều phối; AccountActions, MatchmakingActions, GameplayActions, AdminActions chứa các thao tác theo tính năng. Các lớp Screen chỉ dựng giao diện; các lớp Actions không import JavaFX.
- `ClientScreens` là context dựng giao diện dùng chung và router; các feature screen nhận context này. Model và phần hiển thị tiếp tục tách thành lớp riêng.

Các service phía server dùng context GameServer và chỉ chạy trên engine thread; việc tách package không chuyển dữ liệu engine sang truy cập đồng thời. TCP reader chuyển yêu cầu về engine như trước. Giao thức mạng, entry point `fruit.server.Server`/`fruit.client.Client` và asset vẫn giữ nguyên.

## Database

Schema được thu gọn đúng bốn bảng nghiệp vụ. Không có bảng credentials hoặc schema_history riêng. Phần chuyển đổi v1/v2 đã hoàn tất và được dọn khỏi source. DatabaseSchema chỉ tạo/kiểm tra cấu trúc 4 bảng hiện tại; tệp SQL backup vẫn nằm trong data/backups.

```powershell
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode database
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode server
powershell -ExecutionPolicy Bypass -File .\run.ps1 -Mode client
```

## Kiểm thử và công cụ

- Backend/TCP: `fruit.server.application.GameTest`.
- SQL: `fruit.server.infrastructure.persistence.DatabaseTest`.
- UI: `fruit.server.application.ClientTest`.
- Resource/audio: `fruit.client.features.settings.PresentationTest`.
- Maven và `run.ps1` dùng package hiện tại. Thư mục `tools` giữ `build_audio.py` và `export_atlas.py` để tái tạo tài nguyên game.

Các snapshot source cũ và bản sao benchmark trong target đã được dọn. Tệp SQL sao lưu dữ liệu trong data/backups được giữ lại.
