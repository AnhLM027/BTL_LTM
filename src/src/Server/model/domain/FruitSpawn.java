package Server.model.domain;

public record FruitSpawn(
        long fruitInstanceId,
        long matchId,
        int fruitId,
        int spawnOrder,
        long spawnOffsetMs,
        int xPosition
) {
}
