package Server.model.domain;

import java.time.LocalDateTime;

public record Player(
        long playerId,
        long accountId,
        String displayName,
        int totalScore,
        int totalGames,
        int totalWins,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
