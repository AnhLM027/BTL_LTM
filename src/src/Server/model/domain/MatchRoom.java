package Server.model.domain;

import Server.model.enums.RoomState;

import java.time.LocalDateTime;

public record MatchRoom(
        long roomId,
        long hostPlayerId,
        Long guestPlayerId,
        int modeId,
        RoomState state,
        boolean hostReady,
        boolean guestReady,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean hasGuest() {
        return guestPlayerId != null;
    }

    public boolean bothPlayersReady() {
        return hasGuest() && hostReady && guestReady;
    }
}
