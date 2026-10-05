package Server.repository;

import Server.model.domain.FruitSpawn;
import Server.model.domain.GameMatch;
import Server.model.domain.MatchPlayer;
import Server.model.enums.MatchResult;
import Server.model.enums.MatchState;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public final class MatchRepository {
    public GameMatch create(Connection c, long roomId, int modeId, Integer missionLabelId, Long seed, long host, long guest, List<FruitSpawn> spawns) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("INSERT INTO game_match (room_id,mode_id,mission_label_id,seed,duration_seconds,match_state) VALUES (?,?,?,?,30,'PREPARING')", Statement.RETURN_GENERATED_KEYS)) {
            s.setLong(1, roomId);
            s.setInt(2, modeId);
            if (missionLabelId == null) s.setNull(3, Types.INTEGER);
            else s.setInt(3, missionLabelId);
            s.setLong(4, seed);
            s.executeUpdate();
            try (ResultSet k = s.getGeneratedKeys()) {
                if (!k.next()) throw new SQLException("Missing match id");
                long id = k.getLong(1);
                try (PreparedStatement p = c.prepareStatement("INSERT INTO match_player (match_id,player_id) VALUES (?,?),(?,?)")) {
                    p.setLong(1, id);
                    p.setLong(2, host);
                    p.setLong(3, id);
                    p.setLong(4, guest);
                    p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("INSERT INTO fruit_spawn (match_id,fruit_id,spawn_order,spawn_offset_ms,x_position) VALUES (?,?,?,?,?)")) {
                    for (FruitSpawn f : spawns) {
                        p.setLong(1, id);
                        p.setInt(2, f.fruitId());
                        p.setInt(3, f.spawnOrder());
                        p.setLong(4, f.spawnOffsetMs());
                        p.setInt(5, f.xPosition());
                        p.addBatch();
                    }
                    p.executeBatch();
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE room SET room_state='PREPARING',host_ready=FALSE,guest_ready=FALSE WHERE room_id=?")) {
                    p.setLong(1, roomId);
                    p.executeUpdate();
                }
                return find(c, id, true).orElseThrow();
            }
        }
    }

    public Optional<GameMatch> find(Connection c, long id, boolean lock) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM game_match WHERE match_id=?" + (lock ? " FOR UPDATE" : ""))) {
            s.setLong(1, id);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        }
    }

    public List<GameMatch> findRecoverable(Connection c) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM game_match WHERE match_state IN ('PLAYING','FINALIZING')"); ResultSet r = s.executeQuery()) {
            List<GameMatch> list = new java.util.ArrayList<>();
            while (r.next()) list.add(map(r));
            return list;
        }
    }

    public Optional<GameMatch> findActiveForPlayer(Connection c, long playerId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT m.* FROM game_match m JOIN match_player mp ON mp.match_id=m.match_id WHERE mp.player_id=? AND m.match_state='PLAYING' ORDER BY m.started_at DESC LIMIT 1")) {
            s.setLong(1, playerId);
            try (ResultSet r = s.executeQuery()) {
                return r.next() ? Optional.of(map(r)) : Optional.empty();
            }
        }
    }

    public void start(Connection c, long id) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("UPDATE game_match SET match_state='PLAYING',started_at=CURRENT_TIMESTAMP WHERE match_id=?")) {
            s.setLong(1, id);
            s.executeUpdate();
        }
    }

    public List<FruitSpawn> findSpawns(Connection c, long matchId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT * FROM fruit_spawn WHERE match_id=? ORDER BY spawn_order")) {
            s.setLong(1, matchId);
            try (ResultSet r = s.executeQuery()) {
                List<FruitSpawn> list = new java.util.ArrayList<>();
                while (r.next())
                    list.add(new FruitSpawn(r.getLong("fruit_instance_id"), matchId, r.getInt("fruit_id"), r.getInt("spawn_order"), r.getLong("spawn_offset_ms"), r.getInt("x_position")));
                return list;
            }
        }
    }

    public List<Long> findPlayerIds(Connection c, long matchId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT player_id FROM match_player WHERE match_id=?")) {
            s.setLong(1, matchId);
            try (ResultSet r = s.executeQuery()) {
                List<Long> ids = new java.util.ArrayList<>();
                while (r.next()) ids.add(r.getLong(1));
                return ids;
            }
        }
    }

    public List<MatchPlayer> findPlayers(Connection c, long matchId, boolean lock) throws SQLException {
        String sql = "SELECT * FROM match_player WHERE match_id=? ORDER BY player_id" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setLong(1, matchId);
            try (ResultSet result = statement.executeQuery()) {
                List<MatchPlayer> players = new java.util.ArrayList<>();
                while (result.next()) players.add(mapPlayer(result));
                return players;
            }
        }
    }

    public void markFinalizing(Connection c, long matchId) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("UPDATE game_match SET match_state='FINALIZING' WHERE match_id=? AND match_state='PLAYING'")) {
            statement.setLong(1, matchId);
            if (statement.executeUpdate() != 1) throw new SQLException("Match is no longer playing");
        }
    }

    public void finish(Connection c, long matchId, Long winnerPlayerId) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("UPDATE game_match SET match_state='FINISHED', winner_player_id=?, ended_at=CURRENT_TIMESTAMP WHERE match_id=? AND match_state='FINALIZING'")) {
            if (winnerPlayerId == null) statement.setNull(1, Types.BIGINT);
            else statement.setLong(1, winnerPlayerId);
            statement.setLong(2, matchId);
            if (statement.executeUpdate() != 1) throw new SQLException("Match cannot be finished");
        }
    }

    public void updatePlayerResult(Connection c, long matchId, long playerId, MatchResult result) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("UPDATE match_player SET result=? WHERE match_id=? AND player_id=?")) {
            statement.setString(1, result.name());
            statement.setLong(2, matchId);
            statement.setLong(3, playerId);
            if (statement.executeUpdate() != 1) throw new SQLException("Match player was not found");
        }
    }

    public void updatePlayerStatistics(Connection c, MatchPlayer player, boolean won) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("UPDATE player SET total_score=total_score+?, total_games=total_games+1, total_wins=total_wins+? WHERE player_id=?")) {
            statement.setInt(1, player.finalScore());
            statement.setInt(2, won ? 1 : 0);
            statement.setLong(3, player.playerId());
            if (statement.executeUpdate() != 1) throw new SQLException("Player was not found");
        }
    }

    private GameMatch map(ResultSet r) throws SQLException {
        return new GameMatch(r.getLong("match_id"), r.getLong("room_id"), r.getInt("mode_id"), r.getObject("mission_label_id", Integer.class), r.getObject("seed", Long.class), r.getInt("duration_seconds"), MatchState.valueOf(r.getString("match_state")), r.getObject("winner_player_id", Long.class), timestamp(r, "started_at"), timestamp(r, "ended_at"), r.getTimestamp("created_at").toLocalDateTime());
    }

    private MatchPlayer mapPlayer(ResultSet r) throws SQLException {
        return new MatchPlayer(r.getLong("match_id"), r.getLong("player_id"), r.getInt("correct_count"), r.getInt("wrong_count"), r.getInt("final_score"), r.getString("result") == null ? null : MatchResult.valueOf(r.getString("result")), r.getTimestamp("joined_at").toLocalDateTime());
    }

    private LocalDateTime timestamp(ResultSet r, String c) throws SQLException {
        Timestamp t = r.getTimestamp(c);
        return t == null ? null : t.toLocalDateTime();
    }
}
