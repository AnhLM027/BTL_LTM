package Server.model.domain;

import Server.model.enums.MatchState;

import java.time.LocalDateTime;

public record GameMatch(
        long matchId,
        long roomId,
        int modeId,
        Long seed,
        int durationSeconds,
        MatchState state,
        Long winnerPlayerId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt
) {
    public boolean isPlaying() {
        return state == MatchState.PLAYING;
    }
}
