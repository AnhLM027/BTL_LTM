package Server.model.domain;

import Server.model.enums.MatchResult;

import java.time.LocalDateTime;

public record MatchPlayer(
        long matchId,
        long playerId,
        int correctCount,
        int wrongCount,
        int finalScore,
        MatchResult result,
        LocalDateTime joinedAt
) {
}
