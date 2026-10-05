package Server.service;

import Server.config.DatabaseConfig;
import Server.model.domain.GameMode;
import Server.model.domain.Invite;
import Server.model.domain.MatchRoom;
import Server.model.enums.GameModeCode;
import Server.model.enums.InviteState;
import Server.model.enums.RoomState;
import Server.model.enums.PlayerState;
import Server.repository.GameModeRepository;
import Server.repository.InviteRepository;
import Server.repository.RoomRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

public final class RoomService {
    private static final int INVITE_TTL_SECONDS = 60;

    private final GameModeRepository gameModeRepository;
    private final RoomRepository roomRepository;
    private final InviteRepository inviteRepository;

    public RoomService(GameModeRepository gameModeRepository, RoomRepository roomRepository, InviteRepository inviteRepository) {
        this.gameModeRepository = gameModeRepository;
        this.roomRepository = roomRepository;
        this.inviteRepository = inviteRepository;
    }

    public MatchRoom createRoom(long hostPlayerId, GameModeCode modeCode) throws SQLException, RoomException {
        GameMode mode = gameModeRepository.findActiveByCode(modeCode)
                .orElseThrow(() -> new RoomException("Game mode is unavailable"));
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                if (roomRepository.playerHasActiveRoom(connection, hostPlayerId)) {
                    throw new RoomException("Player already belongs to an active room");
                }
                MatchRoom room = roomRepository.create(connection, hostPlayerId, mode.modeId());
                connection.commit();
                return room;
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public MatchRoom changeGameMode(long hostPlayerId, long roomId, GameModeCode modeCode) throws SQLException, RoomException {
        GameMode mode = gameModeRepository.findActiveByCode(modeCode)
                .orElseThrow(() -> new RoomException("Game mode is unavailable"));
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                MatchRoom room = roomRepository.findById(connection, roomId, true)
                        .orElseThrow(() -> new RoomException("Room was not found"));
                if (room.hostPlayerId() != hostPlayerId
                        || (room.state() != RoomState.WAITING && room.state() != RoomState.FULL)) {
                    throw new RoomException("Only the host can change mode before the match starts");
                }
                MatchRoom updated = roomRepository.changeMode(connection, roomId, hostPlayerId, mode.modeId());
                connection.commit();
                return updated;
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Invite createInvite(long senderPlayerId, long roomId, long receiverPlayerId) throws SQLException, RoomException {
        if (senderPlayerId == receiverPlayerId) {
            throw new RoomException("A player cannot invite themselves");
        }
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                MatchRoom room = roomRepository.findById(connection, roomId, true)
                        .orElseThrow(() -> new RoomException("Room was not found"));
                if (room.hostPlayerId() != senderPlayerId || room.state() != RoomState.WAITING) {
                    throw new RoomException("Only the waiting room host can send an invite");
                }
                if (roomRepository.playerHasActiveRoom(connection, receiverPlayerId)
                        && roomRepository.activeStateForPlayer(connection, receiverPlayerId).orElse(PlayerState.READY) != PlayerState.IN_ROOM) {
                    throw new RoomException("Invited player already belongs to an active room");
                }
                Invite invite = inviteRepository.create(connection, roomId, senderPlayerId, receiverPlayerId,
                        LocalDateTime.now().plusSeconds(INVITE_TTL_SECONDS));
                connection.commit();
                return invite;
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public MatchRoom acceptInvite(long receiverPlayerId, long inviteId) throws SQLException, RoomException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                Invite invite = inviteRepository.findById(connection, inviteId, true)
                        .orElseThrow(() -> new RoomException("Invite was not found"));
                validatePendingInvite(invite, receiverPlayerId);
                MatchRoom room = roomRepository.findById(connection, invite.roomId(), true)
                        .orElseThrow(() -> new RoomException("Room was not found"));
                if (room.hostPlayerId() != invite.senderPlayerId() || room.state() != RoomState.WAITING || room.hasGuest()) {
                    throw new RoomException("Room is no longer available");
                }
                if (roomRepository.playerHasActiveRoom(connection, receiverPlayerId)) {
                    throw new RoomException("Player already belongs to an active room");
                }
                if (!roomRepository.addGuest(connection, room.roomId(), receiverPlayerId)) {
                    throw new RoomException("Room is no longer available");
                }
                inviteRepository.updateState(connection, inviteId, InviteState.ACCEPTED);
                inviteRepository.cancelOtherPendingForRoom(connection, room.roomId(), inviteId);
                MatchRoom updated = roomRepository.findById(connection, room.roomId(), false).orElseThrow();
                connection.commit();
                return updated;
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public Invite rejectInvite(long receiverPlayerId, long inviteId) throws SQLException, RoomException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                Invite invite = inviteRepository.findById(connection, inviteId, true)
                        .orElseThrow(() -> new RoomException("Invite was not found"));
                validatePendingInvite(invite, receiverPlayerId);
                inviteRepository.updateState(connection, inviteId, InviteState.REJECTED);
                Invite rejected = inviteRepository.findById(connection, inviteId, false).orElseThrow();
                connection.commit();
                return rejected;
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public record LeaveRoomOutcome(Optional<MatchRoom> room, Long hostPlayerId, Long guestPlayerId, boolean isHost) {}

    public LeaveRoomOutcome leaveRoom(long playerId, long roomId) throws SQLException, RoomException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                MatchRoom existing = roomRepository.findById(connection, roomId, true)
                        .orElseThrow(() -> new RoomException("Room was not found"));
                Long hostId = existing.hostPlayerId();
                Long guestId = existing.guestPlayerId();
                boolean isHost = (hostId == playerId);

                Optional<MatchRoom> room = roomRepository.leaveRoom(connection, roomId, playerId);
                connection.commit();
                return new LeaveRoomOutcome(room, hostId, guestId, isHost);
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Marks a room as cancelled after its remaining connected participant leaves.
     * This is used to clean up a room whose other participant disconnected and
     * therefore cannot send a normal LEAVE_ROOM message.
     */
    public void cancelRoom(long roomId) throws SQLException, RoomException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                MatchRoom room = roomRepository.findById(connection, roomId, true)
                        .orElseThrow(() -> new RoomException("Room was not found"));
                if (room.state() != RoomState.WAITING && room.state() != RoomState.FULL) {
                    throw new RoomException("Cannot cancel a room that is already " + room.state().name().toLowerCase());
                }
                roomRepository.cancel(connection, roomId);
                connection.commit();
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void validatePendingInvite(Invite invite, long receiverPlayerId) throws RoomException {
        if (invite.receiverPlayerId() != receiverPlayerId) {
            throw new RoomException("Invite belongs to another player");
        }
        if (!invite.isPendingAt(LocalDateTime.now())) {
            throw new RoomException("Invite is no longer pending");
        }
    }
}
