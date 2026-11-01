-- Four business tables. Select the destination database before running this script.
CREATE TABLE IF NOT EXISTS accounts (
    id INT NOT NULL,
    username VARCHAR(24) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
    display_name VARCHAR(96) NOT NULL,
    password_hash VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_salt VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role ENUM('PLAYER','ADMIN') NOT NULL DEFAULT 'PLAYER',
    locked BOOLEAN NOT NULL DEFAULT FALSE,
    avatar_id TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(6) NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uq_accounts_username (username),
    KEY ix_accounts_role_locked (role,locked,id),
    CONSTRAINT ck_accounts_id CHECK (id > 0),
    CONSTRAINT ck_accounts_locked CHECK (locked IN (0,1)),
    CONSTRAINT ck_accounts_avatar CHECK (avatar_id IN (0,1))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT='fruit-battle:v3';

CREATE TABLE IF NOT EXISTS player_stats (
    account_id INT NOT NULL,
    wins INT UNSIGNED NOT NULL DEFAULT 0,
    losses INT UNSIGNED NOT NULL DEFAULT 0,
    draws INT UNSIGNED NOT NULL DEFAULT 0,
    total_score INT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id),
    KEY ix_player_stats_ranking (wins DESC,total_score DESC,account_id),
    CONSTRAINT fk_player_stats_account FOREIGN KEY (account_id) REFERENCES accounts(id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS matches (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    duration_ms INT UNSIGNED NULL,
    finish_reason ENUM('TIME_UP','ACCOUNT_LOCKED') NOT NULL,
    PRIMARY KEY (id),
    KEY ix_matches_created (created_at DESC,id DESC),
    CONSTRAINT ck_matches_duration CHECK (duration_ms <= 60000)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS match_players (
    match_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id INT NOT NULL,
    slot TINYINT UNSIGNED NOT NULL,
    display_name_snapshot VARCHAR(96) NOT NULL,
    outcome ENUM('WIN','LOSS','DRAW') NOT NULL,
    score INT NOT NULL DEFAULT 0,
    correct INT UNSIGNED NOT NULL DEFAULT 0,
    wrong INT UNSIGNED NOT NULL DEFAULT 0,
    bombs INT UNSIGNED NOT NULL DEFAULT 0,
    max_combo INT UNSIGNED NOT NULL DEFAULT 0,
    skills_used INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (match_id,account_id),
    UNIQUE KEY uq_match_players_slot (match_id,slot),
    KEY ix_match_players_account (account_id,match_id),
    CONSTRAINT fk_match_players_match FOREIGN KEY (match_id) REFERENCES matches(id),
    CONSTRAINT fk_match_players_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT ck_match_players_slot CHECK (slot IN (1,2))
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
