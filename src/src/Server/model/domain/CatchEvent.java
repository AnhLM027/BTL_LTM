package Server.model.domain;

import java.time.LocalDateTime;

public record CatchEvent(
        long catchEventId,
        long matchId,
        long playerId,
        long fruitInstanceId,
        CatchResult result,
        int scoreDelta,
        LocalDateTime processedAt
) {
    public enum CatchResult {
        CORRECT,
        WRONG
    }
}
