package Server.model.domain;

import Server.model.enums.InviteState;

import java.time.LocalDateTime;

public record Invite(
        long inviteId,
        long roomId,
        long senderPlayerId,
        long receiverPlayerId,
        InviteState state,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        LocalDateTime respondedAt
) {
    public boolean isPendingAt(LocalDateTime currentTime) {
        return state == InviteState.PENDING
                && (expiresAt == null || currentTime.isBefore(expiresAt));
    }
}
