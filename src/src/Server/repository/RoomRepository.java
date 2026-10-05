package Server.repository;

import Server.model.domain.MatchRoom;
import Server.model.enums.RoomState;
import Server.model.enums.PlayerState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

public final class RoomRepository {
    public Optional<MatchRoom> findActiveForPlayer(Connection connection, long playerId) throws SQLException {
        String query = """
                SELECT * FROM room
                WHERE (host_player_id = ? OR guest_player_id = ?)
                  AND room_state IN ('WAITING', 'FULL', 'PREPARING', 'READY', 'PLAYING', 'WAITING_REMATCH')
                ORDER BY room_id DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, playerId);
            statement.setLong(2, playerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public Optional<PlayerState> activeStateForPlayer(Connection connection, long playerId) throws SQLException {
        String query = """
                SELECT room_state, host_player_id, host_ready, guest_ready FROM room
                WHERE (host_player_id = ? OR guest_player_id = ?)
                  AND room_state IN ('WAITING', 'FULL', 'PREPARING', 'READY', 'PLAYING', 'WAITING_REMATCH')
                ORDER BY room_id DESC
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, playerId);
            statement.setLong(2, playerId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                RoomState state = RoomState.valueOf(result.getString("room_state"));
                if (state == RoomState.PLAYING) return Optional.of(PlayerState.PLAYING);
                boolean host = result.getLong("host_player_id") == playerId;
                boolean ready = host ? result.getBoolean("host_ready") : result.getBoolean("guest_ready");
                return Optional.of(ready ? PlayerState.READY : PlayerState.IN_ROOM);
            }
        }
    }

    public boolean playerHasActiveRoom(Connection connection, long playerId) throws SQLException {
        String query = """
                SELECT 1 FROM room
                WHERE (host_player_id = ? OR guest_player_id = ?)
                  AND room_state IN ('WAITING', 'FULL', 'PREPARING', 'READY', 'PLAYING', 'WAITING_REMATCH')
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, playerId);
            statement.setLong(2, playerId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public MatchRoom create(Connection connection, long hostPlayerId, int modeId) throws SQLException {
        String insert = "INSERT INTO room (host_player_id, mode_id, room_state) VALUES (?, ?, 'WAITING')";
        try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, hostPlayerId);
            statement.setInt(2, modeId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Room creation did not return an identifier");
                }
                return findById(connection, keys.getLong(1), false).orElseThrow();
            }
        }
    }

    public Optional<MatchRoom> findById(Connection connection, long roomId, boolean forUpdate) throws SQLException {
        String query = "SELECT * FROM room WHERE room_id = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, roomId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public MatchRoom changeMode(Connection connection, long roomId, long hostPlayerId, int modeId) throws SQLException {
        String update = "UPDATE room SET mode_id = ?, updated_at = CURRENT_TIMESTAMP "
                + "WHERE room_id = ? AND host_player_id = ? AND room_state IN ('WAITING', 'FULL')";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setInt(1, modeId);
            statement.setLong(2, roomId);
            statement.setLong(3, hostPlayerId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Room is not editable by this host");
            }
        }
        return findById(connection, roomId, false).orElseThrow();
    }

    public boolean addGuest(Connection connection, long roomId, long guestPlayerId) throws SQLException {
        String update = "UPDATE room SET guest_player_id = ?, room_state = 'FULL', updated_at = CURRENT_TIMESTAMP "
                + "WHERE room_id = ? AND guest_player_id IS NULL AND room_state = 'WAITING'";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, guestPlayerId);
            statement.setLong(2, roomId);
            return statement.executeUpdate() == 1;
        }
    }

    public MatchRoom markReady(Connection connection, MatchRoom room, long playerId) throws SQLException {
        String column = room.hostPlayerId() == playerId ? "host_ready" : room.guestPlayerId() != null && room.guestPlayerId() == playerId ? "guest_ready" : null;
        if (column == null) throw new SQLException("Player does not belong to room");
        try (PreparedStatement statement = connection.prepareStatement("UPDATE room SET " + column + "=TRUE WHERE room_id=? AND room_state='PREPARING'")) {
            statement.setLong(1, room.roomId());
            if (statement.executeUpdate() != 1) throw new SQLException("Room is not preparing");
        }
        return findById(connection, room.roomId(), true).orElseThrow();
    }

    public void markPlaying(Connection connection, long roomId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE room SET room_state='PLAYING' WHERE room_id=?")) {
            statement.setLong(1, roomId);
            statement.executeUpdate();
        }
    }

    public void markFinished(Connection connection, long roomId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("UPDATE room SET room_state='FINISHED', host_ready=FALSE, guest_ready=FALSE WHERE room_id=?")) {
            statement.setLong(1, roomId);
            if (statement.executeUpdate() != 1) throw new SQLException("Room was not found");
        }
    }

    public void cancel(Connection connection, long roomId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE room SET room_state='CANCELLED', host_ready=FALSE, guest_ready=FALSE, updated_at=CURRENT_TIMESTAMP WHERE room_id=?")) {
            statement.setLong(1, roomId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Room was not found");
            }
        }
    }

    /**
     * Removes {@code playerId} from the room.
     * <ul>
     *   <li>If the player is the <b>host</b> → the room is set to {@code CANCELLED}.</li>
     *   <li>If the player is the <b>guest</b> → the guest is removed and the room reverts to {@code WAITING}.</li>
     * </ul>
     *
     * @return the updated room, or {@link Optional#empty()} if the room was cancelled.
     */
    public Optional<MatchRoom> leaveRoom(Connection connection, long roomId, long playerId) throws SQLException, Server.service.RoomException {
        MatchRoom room = findById(connection, roomId, true)
                .orElseThrow(() -> new Server.service.RoomException("Room was not found"));
        if (room.state() != RoomState.WAITING && room.state() != RoomState.FULL) {
            throw new Server.service.RoomException("Cannot leave a room that is already " + room.state().name().toLowerCase());
        }
        if (room.hostPlayerId() == playerId) {
            // Host leaves → cancel the entire room
            try (PreparedStatement st = connection.prepareStatement(
                    "UPDATE room SET room_state='CANCELLED', updated_at=CURRENT_TIMESTAMP WHERE room_id=?")) {
                st.setLong(1, roomId);
                st.executeUpdate();
            }
            return Optional.empty(); // room is gone
        } else if (room.guestPlayerId() != null && room.guestPlayerId() == playerId) {
            // Guest leaves → revert to WAITING
            try (PreparedStatement st = connection.prepareStatement(
                    "UPDATE room SET guest_player_id=NULL, room_state='WAITING', updated_at=CURRENT_TIMESTAMP WHERE room_id=?")) {
                st.setLong(1, roomId);
                st.executeUpdate();
            }
            return findById(connection, roomId, false);
        } else {
            throw new Server.service.RoomException("Player does not belong to this room");
        }
    }

    private MatchRoom map(ResultSet result) throws SQLException {
        Long guestPlayerId = result.getObject("guest_player_id", Long.class);
        return new MatchRoom(
                result.getLong("room_id"),
                result.getLong("host_player_id"),
                guestPlayerId,
                result.getInt("mode_id"),
                RoomState.valueOf(result.getString("room_state")),
                result.getBoolean("host_ready"),
                result.getBoolean("guest_ready"),
                result.getTimestamp("created_at").toLocalDateTime(),
                result.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
