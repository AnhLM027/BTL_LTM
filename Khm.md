# Kế hoạch triển khai: CLASSIC + ORDER

## Tổng quan thay đổi

Thiết kế lại 2 game mode từ `FRUIT_GROUP / NUTRITION` → `CLASSIC / ORDER`.
Cả hai mode dùng **1 giỏ cố định**, điều khiển **← / →** duy nhất.

---

## Phase 1 — Thay đổi Database Schema

### 1.1 Cập nhật `game_mode`
```sql
UPDATE game_mode SET mode_code = 'CLASSIC', mode_name = 'Hứng né bom' WHERE mode_code = 'FRUIT_GROUP';
UPDATE game_mode SET mode_code = 'ORDER',   mode_name = 'Hứng theo đơn hàng' WHERE mode_code = 'NUTRITION';
```

### 1.2 Sửa bảng `fruit_spawn` — thêm `is_bomb` và `fall_duration_ms`, cho phép `fruit_id = NULL`

```sql
-- Bỏ FK cũ để cho phép NULL
ALTER TABLE fruit_spawn
    DROP FOREIGN KEY fk_spawn_fruit,
    MODIFY COLUMN fruit_id INT NULL,
    ADD COLUMN is_bomb BOOLEAN NOT NULL DEFAULT FALSE AFTER fruit_id,
    ADD COLUMN fall_duration_ms INT NOT NULL DEFAULT 4000 AFTER x_position;

-- Thêm lại FK chỉ kiểm tra khi fruit_id NOT NULL
ALTER TABLE fruit_spawn
    ADD CONSTRAINT fk_spawn_fruit
        FOREIGN KEY (fruit_id) REFERENCES fruit(fruit_id)
        ON DELETE RESTRICT ON UPDATE CASCADE;

-- Ràng buộc nhất quán: bom ↔ fruit_id NULL, quả ↔ fruit_id NOT NULL
ALTER TABLE fruit_spawn
    ADD CONSTRAINT chk_bomb_consistency
        CHECK (
            (is_bomb = TRUE  AND fruit_id IS NULL) OR
            (is_bomb = FALSE AND fruit_id IS NOT NULL)
        );
```

### 1.3 Thêm bảng `match_target_fruit` — lưu 3 quả mục tiêu cho ORDER mode

```sql
CREATE TABLE match_target_fruit (
    match_id  BIGINT NOT NULL,
    fruit_id  INT    NOT NULL,
    PRIMARY KEY (match_id, fruit_id),
    CONSTRAINT fk_target_match
        FOREIGN KEY (match_id) REFERENCES game_match(match_id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_target_fruit
        FOREIGN KEY (fruit_id) REFERENCES fruit(fruit_id)
        ON DELETE RESTRICT ON UPDATE CASCADE
);
```

### 1.4 Cập nhật `catch_event` — cho phép `-10` (bom trong CLASSIC)

```sql
ALTER TABLE catch_event
    DROP CONSTRAINT chk_score_delta,
    ADD CONSTRAINT chk_score_delta
        CHECK (score_delta IN (10, -5, -10));
```

### 1.5 Đơn giản hóa bảng `basket` — chỉ giữ 1 giỏ chuẩn

```sql
UPDATE basket SET is_active = FALSE;
INSERT INTO basket (group_id, basket_name, asset_path)
VALUES (1, 'Giỏ hứng', 'Client/assets/baskets/standard-basket.png');
-- group_id=1 là placeholder, không dùng để matching nữa
```

---

## Phase 2 — Thay đổi Java Enum & Domain Model

### 2.1 `GameModeCode.java`
```java
public enum GameModeCode {
    CLASSIC,  // Hứng né bom
    ORDER     // Hứng theo đơn hàng
}
```

### 2.2 `FruitSpawn.java` — thêm `isBomb` và `fallDurationMs`
```java
public record FruitSpawn(
    long fruitInstanceId,
    long matchId,
    Integer fruitId,       // NULL nếu là bom
    boolean isBomb,
    int spawnOrder,
    long spawnOffsetMs,
    int xPosition,
    int fallDurationMs     // 4000 hoặc 2500
) {}
```

### 2.3 `MatchPreparation.java` — thêm `targetFruitIds` cho ORDER mode
```java
public record MatchPreparation(
    GameMatch match,
    long hostPlayerId,
    long guestPlayerId,
    List<FruitSpawn> spawns,
    List<Fruit> fruits,
    List<Basket> baskets,
    List<Integer> targetFruitIds  // null = CLASSIC, list of 3 = ORDER
) {}
```

---

## Phase 3 — Server Logic

### 3.1 `MatchService.prepare()` — tạo spawn phù hợp từng mode

**CLASSIC — 20 vật thể: 16 quả + 4 bom**

```
Ràng buộc khi đặt bom:
- Khoảng cách thời gian giữa 2 bom liên tiếp ≥ 2800ms (2 spawn slots)
- Khoảng cách x giữa bom gần nhau trong cùng cửa sổ 3s ≥ 150px
  (giỏ rộng 120px, bom rộng ~38px → cần ≥ 158px để có thể né)
- Không có quá 2 bom trong cùng 10s cuối
- Sau khi đặt bom, kiểm tra còn ít nhất 1 "vùng an toàn" (x ∈ [0,280] rộng ≥ 130px)

Thuật toán:
1. Tạo 20 slot spawn (spawnOffsetMs: 0, 1400, 2800, ..., 26600ms)
2. Chọn ngẫu nhiên 4 vị trí slot cho bom, thỏa ràng buộc trên
3. Slot còn lại: gán fruit ngẫu nhiên từ catalog
4. fall_duration_ms:
   - spawnOffsetMs < 20000ms → fallDurationMs = 4000
   - spawnOffsetMs >= 20000ms → fallDurationMs = 2500
   - Kiểm tra: spawnOffsetMs + fallDurationMs ≤ 30000ms (đảm bảo vật thể kịp xuống giỏ)
```

**ORDER — 20 quả, không có bom**

```
1. Chọn ngẫu nhiên 3 fruit_id từ catalog → lưu vào match_target_fruit
2. Spawn 20 quả: mix ngẫu nhiên giữa target fruits và non-target
   - Tỷ lệ đề xuất: ~12 target + ~8 non-target (đủ để cả 2 loại xuất hiện thường xuyên)
3. fall_duration_ms: tương tự CLASSIC
```

### 3.2 `CatchEventRepository.process()` — xử lý bom và combo

**Thay đổi scoring:**

| Sự kiện | score_delta | catch_result |
|---|---|---|
| CLASSIC: hứng quả | +10 | CORRECT |
| CLASSIC: hứng bom | -10 | WRONG |
| ORDER: hứng quả trong đơn | +10 | CORRECT |
| ORDER: hứng quả ngoài đơn | -5 | WRONG |

**Trả về thêm thông tin để handler tính combo:**
```java
public record ScoreUpdate(
    long matchId,
    long playerId,
    int score,
    int correctCount,
    int wrongCount,
    boolean wasCorrect   // true = CORRECT, false = WRONG → dùng để reset/tăng combo
) {}
```

### 3.3 `GameServerEventHandler` — Combo state in-memory

```java
// Thêm field:
private final ConcurrentHashMap<Long, Map<Long, Integer>> comboState = new ConcurrentHashMap<>();
// key: matchId → (playerId → comboCount)

// Trong catchEvent():
ScoreUpdate score = matchService.catchFruit(...);
Map<Long, Integer> matchCombo = comboState.computeIfAbsent(matchId, k -> new ConcurrentHashMap<>());
int combo = matchCombo.getOrDefault(playerId, 0);

if (score.wasCorrect()) {
    combo++;
    int bonus = 0;
    if (combo >= 5) {
        bonus = 10;
        combo = 0;  // reset, bắt đầu chuỗi mới
    }
    matchCombo.put(playerId, combo);
    // Gửi SCORE_UPDATE với bonus nếu có
} else {
    matchCombo.put(playerId, 0);  // reset combo
}

// Dọn dẹp khi match kết thúc:
comboState.remove(matchId);  // trong scheduleFinalization()
```

**Protocol message `SCORE_UPDATE` bổ sung thêm:**
```
comboCount   → tiến độ combo hiện tại (0-4)
comboBonus   → 10 nếu vừa đạt 5 combo, 0 nếu không
```

---

## Phase 4 — Client `RunGame.java`

### 4.1 Loại bỏ hoàn toàn basket switching
- Xóa `basketIndex`, `baskets` list → chỉ giữ `basketX` (vị trí x của giỏ)
- Xóa xử lý phím số và ↑/↓
- `handleKey()` chỉ xử lý `VK_LEFT` và `VK_RIGHT`
- `sendBasketPosition()` chỉ gửi `x`, không gửi `basketIndex`
- Xóa `digitIndex()` helper vừa thêm

### 4.2 Render bom
```java
private record SpawnInstance(
    long instanceId,
    Integer fruitId,   // null nếu bom
    boolean isBomb,
    long spawnOffsetMs,
    int xPosition,
    int fallDurationMs
) {}

// Trong paintComponent():
int y = 80 + (int) Math.min(425, (elapsed - spawn.spawnOffsetMs()) * 425L / spawn.fallDurationMs());

if (spawn.isBomb()) {
    // Render hình bom (asset hoặc hình tròn đen + icon ⚠)
    drawBomb(g, x, y);
} else {
    // Render quả như cũ
    drawFruit(g, x, y, fruits.get(spawn.fruitId()));
}
```

### 4.3 ORDER mode — hiển thị 3 quả mục tiêu thường trực
```java
private final List<Integer> targetFruitIds = new ArrayList<>();

// Trong handleMessage() → MATCH_PREPARE:
// Server gửi thêm targetFruitId_1, targetFruitId_2, targetFruitId_3

// Trong paintComponent() — vẽ overlay đơn hàng:
if (isOrderMode && !targetFruitIds.isEmpty()) {
    drawOrderOverlay(g);
}

private void drawOrderOverlay(Graphics2D g) {
    // Panel nhỏ ở góc trên giữa màn hình local player
    // Hiển thị "Đơn hàng:" + 3 ảnh quả cạnh nhau
    g.setColor(new Color(255, 255, 220, 210));
    g.fillRoundRect(420, 38, 180, 55, 12, 12);
    g.drawString("Hứng:", 428, 56);
    for (int i = 0; i < targetFruitIds.size(); i++) {
        ImageIcon icon = asset(fruits.get(targetFruitIds.get(i)).assetPath());
        if (icon != null) g.drawImage(icon.getImage(), 468 + i * 40, 42, 34, 34, null);
    }
}
```

### 4.4 Combo UI
```java
private int comboCount = 0;

// Trong updateScore() — đọc comboCount và comboBonus từ SCORE_UPDATE
// Trong paintComponent() — vẽ 5 chấm combo
private void drawComboBar(Graphics2D g) {
    for (int i = 0; i < 5; i++) {
        g.setColor(i < comboCount ? new Color(255, 200, 0) : new Color(180, 180, 180, 120));
        g.fillOval(412 + i * 18, 540, 12, 12);
    }
}
```

---

## Phase 5 — Repository cần cập nhật

| Repository | Thay đổi |
|---|---|
| `MatchRepository` | `findSpawns()` → đọc thêm `is_bomb`, `fall_duration_ms`; `create()` → lưu 2 cột mới |
| `MatchRepository` | Thêm `saveTargetFruits(matchId, List<Integer>)` và `findTargetFruits(matchId)` |
| `CatchEventRepository` | Kiểm tra `is_bomb` từ `fruit_spawn`; kiểm tra `match_target_fruit` cho ORDER; tính đúng `score_delta` |
| `GameModeRepository` | Không đổi logic, chỉ thay enum |

---

## Thứ tự triển khai đề xuất

```
[1] Sửa schema.sql (ALTER TABLE + CREATE TABLE)
[2] Cập nhật enum GameModeCode → CLASSIC, ORDER
[3] Cập nhật domain: FruitSpawn, MatchPreparation, ScoreUpdate
[4] Sửa MatchRepository (SQL queries)
[5] Sửa CatchEventRepository (scoring logic)
[6] Sửa MatchService.prepare() (spawn generation)
[7] Sửa GameServerEventHandler (combo state, send target fruits)
[8] Sửa RunGame.java (client render + controls)
[9] Test end-to-end cả 2 mode
```

---

## Điểm rủi ro cần kiểm tra

> [!CAUTION]
> Sau khi ALTER TABLE `fruit_spawn`, cần migrate dữ liệu cũ:
> - Các row cũ có `fruit_id NOT NULL` → set `is_bomb = FALSE`, `fall_duration_ms = 4000`
> - Chạy `schema.sql` trên DB mới thì không cần bước này

> [!WARNING]
> Ràng buộc `chk_bomb_consistency` chỉ hoạt động trên MySQL 8.0.16+.
> Trên MySQL 5.7, CHECK constraint được parse nhưng không enforce — cần validate ở tầng service.

> [!NOTE]
> `comboState` trong handler là in-memory. Nếu server restart giữa chừng, combo bị mất
> nhưng điểm tích lũy trong DB vẫn đúng — chấp nhận được cho phiên bản đầu.
