package Server.model.domain;

public record FruitSpawn(
        long fruitInstanceId,
        long matchId,
        Integer fruitId,
        boolean isBomb,
        int spawnOrder,
        long spawnOffsetMs,
        int xPosition,
        int fallDurationMs
) {
}
