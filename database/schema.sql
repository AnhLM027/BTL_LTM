CREATE DATABASE IF NOT EXISTS fruit_battle_online
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE fruit_battle_online;

-- =========================================================
-- 1. ACCOUNT
-- =========================================================
CREATE TABLE account (
    account_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_account_role
        CHECK (role IN ('PLAYER', 'ADMIN')),

    CONSTRAINT chk_account_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED'))
);

-- =========================================================
-- 2. PLAYER
-- =========================================================
-- Only accounts with role PLAYER receive a row here. ADMIN accounts are
-- authenticated from account alone and must never participate in gameplay.
CREATE TABLE player (
    player_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,

    total_score INT NOT NULL DEFAULT 0,
    total_games INT NOT NULL DEFAULT 0,
    total_wins INT NOT NULL DEFAULT 0,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_player_account
        FOREIGN KEY (account_id)
        REFERENCES account(account_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- =========================================================
-- 3. GAME MODE
-- =========================================================
CREATE TABLE game_mode (
    mode_id INT AUTO_INCREMENT PRIMARY KEY,
    mode_code VARCHAR(50) NOT NULL UNIQUE,
    mode_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- =========================================================
-- 4. FRUIT GROUP
-- =========================================================
CREATE TABLE fruit_group (
    group_id INT AUTO_INCREMENT PRIMARY KEY,
    group_code VARCHAR(50) NOT NULL UNIQUE,
    group_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- =========================================================
-- 5. NUTRITION LABEL
-- =========================================================
CREATE TABLE nutrition_label (
    label_id INT AUTO_INCREMENT PRIMARY KEY,
    label_code VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- =========================================================
-- 6. FRUIT
-- =========================================================
CREATE TABLE fruit (
    fruit_id INT AUTO_INCREMENT PRIMARY KEY,
    group_id INT NOT NULL,
    fruit_code VARCHAR(50) NOT NULL UNIQUE,
    fruit_name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    default_asset_path VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_fruit_group
        FOREIGN KEY (group_id)
        REFERENCES fruit_group(group_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE
);

-- =========================================================
-- 7. FRUIT ASSET
-- =========================================================
CREATE TABLE fruit_asset (
    asset_id INT AUTO_INCREMENT PRIMARY KEY,
    fruit_id INT NOT NULL,
    asset_type VARCHAR(50),
    asset_path VARCHAR(500) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_asset_fruit
        FOREIGN KEY (fruit_id)
        REFERENCES fruit(fruit_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- =========================================================
-- 8. FRUIT - NUTRITION
-- Many-to-Many
-- =========================================================
CREATE TABLE fruit_nutrition (
    fruit_id INT NOT NULL,
    label_id INT NOT NULL,

    PRIMARY KEY (fruit_id, label_id),

    CONSTRAINT fk_fruit_nutrition_fruit
        FOREIGN KEY (fruit_id)
        REFERENCES fruit(fruit_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_fruit_nutrition_label
        FOREIGN KEY (label_id)
        REFERENCES nutrition_label(label_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- =========================================================
-- 9. BASKET
-- Gameplay dùng một giỏ cố định; giỏ không còn liên kết với fruit_group.
-- =========================================================
CREATE TABLE basket (
    basket_id INT AUTO_INCREMENT PRIMARY KEY,
    basket_name VARCHAR(100) NOT NULL,
    asset_path VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- =========================================================
-- 10. ROOM
-- Một Room có thể sinh nhiều GAME_MATCH để hỗ trợ REMATCH
-- =========================================================
CREATE TABLE room (
    room_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    host_player_id BIGINT NOT NULL,
    guest_player_id BIGINT NULL,
    mode_id INT NOT NULL,

    room_state VARCHAR(30) NOT NULL DEFAULT 'WAITING',

    host_ready BOOLEAN NOT NULL DEFAULT FALSE,
    guest_ready BOOLEAN NOT NULL DEFAULT FALSE,

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_room_host
        FOREIGN KEY (host_player_id)
        REFERENCES player(player_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_room_guest
        FOREIGN KEY (guest_player_id)
        REFERENCES player(player_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_room_mode
        FOREIGN KEY (mode_id)
        REFERENCES game_mode(mode_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_room_players
        CHECK (
            guest_player_id IS NULL
            OR host_player_id <> guest_player_id
        ),

    CONSTRAINT chk_room_state
        CHECK (
            room_state IN (
                'WAITING',
                'FULL',
                'PREPARING',
                'READY',
                'PLAYING',
                'WAITING_REMATCH',
                'FINISHED',
                'CANCELLED'
            )
        )
);

-- =========================================================
-- 11. INVITE
-- =========================================================
CREATE TABLE invite (
    invite_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    room_id BIGINT NOT NULL,
    sender_player_id BIGINT NOT NULL,
    receiver_player_id BIGINT NOT NULL,

    invite_state VARCHAR(30) NOT NULL DEFAULT 'PENDING',

    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL,
    responded_at DATETIME NULL,

    CONSTRAINT fk_invite_room
        FOREIGN KEY (room_id)
        REFERENCES room(room_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_invite_sender
        FOREIGN KEY (sender_player_id)
        REFERENCES player(player_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_invite_receiver
        FOREIGN KEY (receiver_player_id)
        REFERENCES player(player_id)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT chk_invite_players
        CHECK (sender_player_id <> receiver_player_id),

    CONSTRAINT chk_invite_state
        CHECK (
            invite_state IN (
                'PENDING',
                'ACCEPTED',
                'REJECTED',
                'EXPIRED',
                'CANCELLED'
            )
        )
);

-- =========================================================
-- 12. GAME MATCH
-- Một room có thể có nhiều match => REMATCH
-- =========================================================
CREATE TABLE game_match (
    match_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    room_id BIGINT NOT NULL,
    mode_id INT NOT NULL,

    seed BIGINT NULL,

    duration_seconds INT NOT NULL DEFAULT 30,

    match_state VARCHAR(30) NOT NULL DEFAULT 'PREPARING',

    -- NULL nếu hòa hoặc trận chưa kết thúc
    winner_player_id BIGINT NULL,

    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_match_room
        FOREIGN KEY (room_id)
        REFERENCES room(room_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_match_mode
        FOREIGN KEY (mode_id)
        REFERENCES game_mode(mode_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_match_winner
        FOREIGN KEY (winner_player_id)
        REFERENCES player(player_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_match_duration
        CHECK (duration_seconds = 30),

    CONSTRAINT chk_match_state
        CHECK (
            match_state IN (
                'PREPARING',
                'READY',
                'PLAYING',
                'FINALIZING',
                'FINISHED',
                'CANCELLED'
            )
        )
);

-- =========================================================
-- 13. MATCH PLAYER
-- =========================================================
CREATE TABLE match_player (
    match_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,

    correct_count INT NOT NULL DEFAULT 0,
    wrong_count INT NOT NULL DEFAULT 0,
    final_score INT NOT NULL DEFAULT 0,

    result VARCHAR(20) NULL,

    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (match_id, player_id),

    CONSTRAINT fk_match_player_match
        FOREIGN KEY (match_id)
        REFERENCES game_match(match_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_match_player_player
        FOREIGN KEY (player_id)
        REFERENCES player(player_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_correct_count
        CHECK (correct_count >= 0),

    CONSTRAINT chk_wrong_count
        CHECK (wrong_count >= 0),

    CONSTRAINT chk_match_result
        CHECK (
            result IS NULL
            OR result IN ('WIN', 'LOSE', 'DRAW')
        )
);

-- =========================================================
-- 14. FRUIT SPAWN
-- Lịch sinh vật thể dùng chung cho hai client. Bom có fruit_id = NULL.
-- =========================================================
CREATE TABLE fruit_spawn (
    fruit_instance_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    match_id BIGINT NOT NULL,
    fruit_id INT NULL,
    -- Quy tắc is_bomb ↔ fruit_id được MatchService kiểm tra ở tầng server.
    -- MySQL không cho CHECK này cùng với FK fruit_id có referential action.
    is_bomb BOOLEAN NOT NULL DEFAULT FALSE,

    spawn_order INT NOT NULL,

    -- Số milliseconds kể từ MATCH_START
    spawn_offset_ms BIGINT NOT NULL,

    x_position INT NOT NULL,
    fall_duration_ms INT NOT NULL DEFAULT 4000,

    CONSTRAINT fk_spawn_match
        FOREIGN KEY (match_id)
        REFERENCES game_match(match_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_spawn_fruit
        FOREIGN KEY (fruit_id)
        REFERENCES fruit(fruit_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT uk_match_spawn_order
        UNIQUE (match_id, spawn_order),

    CONSTRAINT chk_spawn_offset
        CHECK (spawn_offset_ms BETWEEN 0 AND 26600),

    CONSTRAINT chk_spawn_x_position
        CHECK (x_position BETWEEN 0 AND 758),

    CONSTRAINT chk_fall_duration
        CHECK (fall_duration_ms IN (2500, 4000)),

    CONSTRAINT chk_spawn_lands_before_match_end
        CHECK (spawn_offset_ms + fall_duration_ms <= 30000)
);

-- =========================================================
-- 15. MATCH TARGET FRUIT
-- Ba quả mục tiêu của một ORDER, theo đúng thứ tự hiển thị cho client.
-- =========================================================
CREATE TABLE match_target_fruit (
    match_id BIGINT NOT NULL,
    target_order TINYINT NOT NULL,
    fruit_id INT NOT NULL,

    PRIMARY KEY (match_id, target_order),

    CONSTRAINT uk_match_target_fruit
        UNIQUE (match_id, fruit_id),

    CONSTRAINT fk_target_match
        FOREIGN KEY (match_id)
        REFERENCES game_match(match_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_target_fruit
        FOREIGN KEY (fruit_id)
        REFERENCES fruit(fruit_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT chk_target_order
        CHECK (target_order IN (1, 2, 3))
);

-- =========================================================
-- 16. CATCH EVENT
-- =========================================================
CREATE TABLE catch_event (
    catch_event_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    match_id BIGINT NOT NULL,
    player_id BIGINT NOT NULL,
    fruit_instance_id BIGINT NOT NULL,

    catch_result VARCHAR(20) NOT NULL,

    score_delta INT NOT NULL,

    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_catch_match
        FOREIGN KEY (match_id)
        REFERENCES game_match(match_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    CONSTRAINT fk_catch_player
        FOREIGN KEY (player_id)
        REFERENCES player(player_id)
        ON DELETE RESTRICT
        ON UPDATE CASCADE,

    CONSTRAINT fk_catch_fruit_instance
        FOREIGN KEY (fruit_instance_id)
        REFERENCES fruit_spawn(fruit_instance_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- Một quả chỉ được tính một lần cho mỗi player trong một match
    CONSTRAINT uk_catch_once
        UNIQUE (match_id, player_id, fruit_instance_id),

    CONSTRAINT chk_catch_result
        CHECK (
            catch_result IN ('CORRECT', 'WRONG')
        ),

    CONSTRAINT chk_score_delta
        CHECK (
            score_delta IN (10, -5, -10)
        )
);

-- =========================================================
-- INDEXES
-- =========================================================

CREATE INDEX idx_room_host
ON room(host_player_id);

CREATE INDEX idx_room_guest
ON room(guest_player_id);

CREATE INDEX idx_room_state
ON room(room_state);

CREATE INDEX idx_invite_receiver_state
ON invite(receiver_player_id, invite_state);

CREATE INDEX idx_invite_room
ON invite(room_id);

CREATE INDEX idx_match_room
ON game_match(room_id);

CREATE INDEX idx_match_started_at
ON game_match(started_at);

CREATE INDEX idx_match_player_player
ON match_player(player_id);

CREATE INDEX idx_spawn_match
ON fruit_spawn(match_id);

CREATE INDEX idx_catch_match_player
ON catch_event(match_id, player_id);

-- =========================================================
-- INITIAL DEVELOPMENT ACCOUNTS
-- Passwords are stored with the same PBKDF2 format used by PasswordHasher.
-- =========================================================

INSERT INTO account (username, password_hash, role, status)
VALUES
    ('u1', 'pbkdf2$210000$LsJ1tvIJ5RRVX4l3lFnkGw$NVX8GH5E6weDtCOzLfZNyB48e0_d9eIa1sDj6UiWlwY', 'PLAYER', 'ACTIVE'),
    ('u2', 'pbkdf2$210000$LsJ1tvIJ5RRVX4l3lFnkGw$NVX8GH5E6weDtCOzLfZNyB48e0_d9eIa1sDj6UiWlwY', 'PLAYER', 'ACTIVE'),
    ('u3', 'pbkdf2$210000$LsJ1tvIJ5RRVX4l3lFnkGw$NVX8GH5E6weDtCOzLfZNyB48e0_d9eIa1sDj6UiWlwY', 'PLAYER', 'ACTIVE'),
    ('admin', 'pbkdf2$210000$LsJ1tvIJ5RRVX4l3lFnkGw$NVX8GH5E6weDtCOzLfZNyB48e0_d9eIa1sDj6UiWlwY', 'ADMIN', 'ACTIVE');

INSERT INTO player (account_id, display_name)
SELECT account_id,
       CASE username
           WHEN 'u1' THEN 'Người chơi 1'
           WHEN 'u2' THEN 'Người chơi 2'
           WHEN 'u3' THEN 'Người chơi 3'
       END
FROM account
WHERE username IN ('u1', 'u2', 'u3');

-- =========================================================
-- INITIAL REFERENCE DATA
-- =========================================================

INSERT INTO game_mode (
    mode_code,
    mode_name,
    description
)
VALUES
(
    'CLASSIC',
    'Hứng né bom',
    'Trận đấu 1v1 kéo dài 30 giây. Dùng phím mũi tên trái/phải để di chuyển một giỏ cố định. Hứng hoa quả được +10 điểm, hứng bom bị -10 điểm. Bỏ qua vật thể không bị trừ điểm.'
),
(
    'ORDER',
    'Hứng theo đơn hàng',
    'Trận đấu 1v1 kéo dài 30 giây. Mỗi trận có ba quả mục tiêu. Dùng phím mũi tên trái/phải để hứng quả trong đơn hàng: đúng +10 điểm, ngoài đơn -5 điểm.'
);

INSERT INTO fruit_group (group_code, group_name, description)
VALUES
    ('CITRUS', 'Nhóm quả có múi', 'Cam, chanh và các loại quả có múi'),
    ('BERRY', 'Nhóm quả mọng', 'Dâu, việt quất và các loại quả mọng'),
    ('STONE_FRUIT', 'Nhóm quả hạch', 'Đào, mơ và các loại quả có hạch'),
    ('TROPICAL', 'Nhóm quả nhiệt đới', 'Xoài, dứa, đu đủ và các loại quả nhiệt đới'),
    ('POME', 'Nhóm quả có lõi', 'Táo, lê và các loại quả có lõi'),
    ('MELON', 'Nhóm dưa', 'Dưa hấu, dưa lưới và các loại dưa'),
    ('BANANA', 'Nhóm chuối', 'Chuối và các giống chuối');

INSERT INTO nutrition_label (label_code, display_name, description)
VALUES
    ('VITAMIN_C', 'Giàu Vitamin C', 'Hoa quả giàu vitamin C'),
    ('POTASSIUM', 'Giàu Kali', 'Hoa quả giàu kali'),
    ('FIBER', 'Giàu chất xơ', 'Hoa quả giàu chất xơ'),
    ('VITAMIN_A', 'Giàu Vitamin A', 'Hoa quả giàu vitamin A'),
    ('ANTIOXIDANT', 'Giàu chất chống oxy hóa', 'Hoa quả chứa nhiều chất chống oxy hóa'),
    ('FOLATE', 'Giàu Folate', 'Hoa quả giàu folate'),
    ('MANGANESE', 'Giàu Mangan', 'Hoa quả giàu khoáng chất mangan');

INSERT INTO fruit (group_id, fruit_code, fruit_name, description, default_asset_path)
VALUES
    ((SELECT group_id FROM fruit_group WHERE group_code = 'CITRUS'), 'ORANGE', 'Cam', 'Cam có vỏ màu cam, nhiều nước và vị ngọt pha chua nhẹ. Cam chứa Vitamin C và chất xơ, giúp hỗ trợ hệ miễn dịch, sức khỏe làn da và hoạt động tiêu hóa.', 'Client/assets/fruits/orange.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'CITRUS'), 'LEMON', 'Chanh', 'Chanh có vỏ vàng hoặc xanh, mùi thơm đặc trưng và vị chua rõ rệt. Chanh chứa nhiều Vitamin C, giúp hỗ trợ hệ miễn dịch, hình thành collagen và bảo vệ tế bào.', 'Client/assets/fruits/lemon.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'BERRY'), 'STRAWBERRY', 'Dâu tây', 'Dâu tây có màu đỏ, thịt mềm và vị chua ngọt đặc trưng. Dâu tây chứa Vitamin C, chất xơ và các chất chống oxy hóa, giúp hỗ trợ hệ miễn dịch, tiêu hóa và bảo vệ tế bào.', 'Client/assets/fruits/strawberry.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'BERRY'), 'BLUEBERRY', 'Việt quất', 'Việt quất là quả mọng nhỏ màu xanh tím, có vị ngọt dịu hoặc hơi chua. Việt quất chứa chất xơ và nhiều hợp chất chống oxy hóa, giúp hỗ trợ sức khỏe tế bào, tim mạch và hệ tiêu hóa.', 'Client/assets/fruits/blueberry.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'STONE_FRUIT'), 'PEACH', 'Đào', 'Đào có lớp vỏ mỏng, thịt mềm, nhiều nước và vị ngọt thơm. Đào chứa Vitamin C, chất xơ và Kali, giúp hỗ trợ hệ miễn dịch, tiêu hóa và hoạt động bình thường của cơ bắp.', 'Client/assets/fruits/peach.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'STONE_FRUIT'), 'APRICOT', 'Mơ', 'Mơ là quả nhỏ màu vàng cam, có thịt mềm và vị chua ngọt. Mơ chứa beta-carotene, Kali và các chất chống oxy hóa, giúp hỗ trợ thị lực, hoạt động cơ bắp và bảo vệ tế bào.', 'Client/assets/fruits/apricot.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'TROPICAL'), 'MANGO', 'Xoài', 'Xoài có thịt mềm, vị ngọt và mùi thơm đặc trưng khi chín. Xoài chứa Vitamin A, Vitamin C và chất xơ, giúp hỗ trợ thị lực, hệ miễn dịch và hoạt động tiêu hóa.', 'Client/assets/fruits/mango.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'TROPICAL'), 'PINEAPPLE', 'Dứa', 'Dứa có lớp vỏ nhiều mắt, thịt mọng nước và vị chua ngọt đặc trưng. Dứa chứa Vitamin C và Mangan, giúp hỗ trợ hệ miễn dịch, chuyển hóa năng lượng và duy trì sức khỏe xương.', 'Client/assets/fruits/pineapple.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'TROPICAL'), 'PAPAYA', 'Đu đủ', 'Đu đủ chín có thịt mềm màu cam, vị ngọt nhẹ và nhiều nước. Đu đủ chứa Vitamin C, tiền Vitamin A, Folate và chất xơ, giúp hỗ trợ hệ miễn dịch, thị lực và hoạt động tiêu hóa.', 'Client/assets/fruits/papaya.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'POME'), 'APPLE', 'Táo', 'Táo có thịt giòn, nhiều màu sắc và hương vị từ chua đến ngọt. Táo chứa chất xơ và các hợp chất chống oxy hóa, giúp hỗ trợ hệ tiêu hóa, sức khỏe tim mạch và bảo vệ tế bào.', 'Client/assets/fruits/apple.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'POME'), 'PEAR', 'Lê', 'Lê có thịt nhiều nước, vị ngọt dịu và kết cấu từ giòn đến mềm tùy giống. Lê chứa nhiều nước và chất xơ, giúp hỗ trợ bổ sung nước, tạo cảm giác no và duy trì hoạt động tiêu hóa.', 'Client/assets/fruits/pear.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'MELON'), 'WATERMELON', 'Dưa hấu', 'Dưa hấu có thịt mọng nước, thường có màu đỏ hoặc vàng và vị ngọt mát. Dưa hấu chứa nhiều nước, Vitamin C và một số tiền Vitamin A, giúp hỗ trợ bổ sung nước, hệ miễn dịch và sức khỏe làn da.', 'Client/assets/fruits/watermelon.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'MELON'), 'CANTALOUPE', 'Dưa lưới', 'Dưa lưới có thịt mềm, mọng nước, vị ngọt và mùi thơm đặc trưng. Dưa lưới chứa nhiều nước, Vitamin A và Vitamin C, giúp hỗ trợ bổ sung nước, thị lực và hệ miễn dịch.', 'Client/assets/fruits/cantaloupe.png'),
    ((SELECT group_id FROM fruit_group WHERE group_code = 'BANANA'), 'BANANA', 'Chuối', 'Chuối có thịt mềm, vị ngọt và kết cấu đặc trưng khi chín. Chuối chứa Kali, Vitamin B6 và chất xơ, giúp hỗ trợ hoạt động cơ bắp, chuyển hóa năng lượng và hệ tiêu hóa.', 'Client/assets/fruits/banana.png');

INSERT INTO fruit_nutrition (fruit_id, label_id)
VALUES
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'ORANGE'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_C')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'ORANGE'), (SELECT label_id FROM nutrition_label WHERE label_code = 'FIBER')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'LEMON'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_C')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'STRAWBERRY'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_C')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'STRAWBERRY'), (SELECT label_id FROM nutrition_label WHERE label_code = 'FIBER')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'BLUEBERRY'), (SELECT label_id FROM nutrition_label WHERE label_code = 'FIBER')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'PEACH'), (SELECT label_id FROM nutrition_label WHERE label_code = 'POTASSIUM')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'APRICOT'), (SELECT label_id FROM nutrition_label WHERE label_code = 'POTASSIUM')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'MANGO'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_A')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'MANGO'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_C')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'PINEAPPLE'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_C')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'PINEAPPLE'), (SELECT label_id FROM nutrition_label WHERE label_code = 'MANGANESE')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'PAPAYA'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_A')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'PAPAYA'), (SELECT label_id FROM nutrition_label WHERE label_code = 'FOLATE')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'APPLE'), (SELECT label_id FROM nutrition_label WHERE label_code = 'FIBER')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'APPLE'), (SELECT label_id FROM nutrition_label WHERE label_code = 'ANTIOXIDANT')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'PEAR'), (SELECT label_id FROM nutrition_label WHERE label_code = 'FIBER')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'WATERMELON'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_A')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'WATERMELON'), (SELECT label_id FROM nutrition_label WHERE label_code = 'ANTIOXIDANT')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'CANTALOUPE'), (SELECT label_id FROM nutrition_label WHERE label_code = 'VITAMIN_A')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'BANANA'), (SELECT label_id FROM nutrition_label WHERE label_code = 'POTASSIUM')),
    ((SELECT fruit_id FROM fruit WHERE fruit_code = 'BANANA'), (SELECT label_id FROM nutrition_label WHERE label_code = 'FIBER'));

INSERT INTO basket (basket_name, asset_path)
VALUES
    ('Giỏ hứng', 'Client/assets/baskets/standard-basket.png');

INSERT INTO fruit_asset (fruit_id, asset_type, asset_path, is_default)
SELECT fruit_id, 'SPRITE', default_asset_path, TRUE
FROM fruit;
