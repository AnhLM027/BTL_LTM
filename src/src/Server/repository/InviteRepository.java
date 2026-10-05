package Server.repository;

import Server.model.domain.Invite;
import Server.model.enums.InviteState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

public final class InviteRepository {
    public Invite create(Connection connection, long roomId, long senderPlayerId, long receiverPlayerId,
                         LocalDateTime expiresAt) throws SQLException {
        String insert = "INSERT INTO invite (room_id, sender_player_id, receiver_player_id, expires_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, roomId);
            statement.setLong(2, senderPlayerId);
            statement.setLong(3, receiverPlayerId);
            statement.setTimestamp(4, java.sql.Timestamp.valueOf(expiresAt));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Invite creation did not return an identifier");
                }
                return findById(connection, keys.getLong(1), false).orElseThrow();
            }
        }
    }

    public Optional<Invite> findById(Connection connection, long inviteId, boolean forUpdate) throws SQLException {
        String query = "SELECT * FROM invite WHERE invite_id = ?" + (forUpdate ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setLong(1, inviteId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        }
    }

    public void updateState(Connection connection, long inviteId, InviteState state) throws SQLException {
        String update = "UPDATE invite SET invite_state = ?, responded_at = CURRENT_TIMESTAMP WHERE invite_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setString(1, state.name());
            statement.setLong(2, inviteId);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Invite was not found");
            }
        }
    }

    public void cancelOtherPendingForRoom(Connection connection, long roomId, long acceptedInviteId) throws SQLException {
        String update = "UPDATE invite SET invite_state = 'CANCELLED', responded_at = CURRENT_TIMESTAMP "
                + "WHERE room_id = ? AND invite_id <> ? AND invite_state = 'PENDING'";
        try (PreparedStatement statement = connection.prepareStatement(update)) {
            statement.setLong(1, roomId);
            statement.setLong(2, acceptedInviteId);
            statement.executeUpdate();
        }
    }

    private Invite map(ResultSet result) throws SQLException {
        java.sql.Timestamp expiresAt = result.getTimestamp("expires_at");
        java.sql.Timestamp respondedAt = result.getTimestamp("responded_at");
        return new Invite(
                result.getLong("invite_id"),
                result.getLong("room_id"),
                result.getLong("sender_player_id"),
                result.getLong("receiver_player_id"),
                InviteState.valueOf(result.getString("invite_state")),
                result.getTimestamp("created_at").toLocalDateTime(),
                expiresAt == null ? null : expiresAt.toLocalDateTime(),
                respondedAt == null ? null : respondedAt.toLocalDateTime()
        );
    }
}
