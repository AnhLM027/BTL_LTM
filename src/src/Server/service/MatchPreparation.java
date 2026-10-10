package Server.service;

import Server.model.domain.FruitSpawn;
import Server.model.domain.GameMatch;
import Server.model.domain.Fruit;
import Server.model.domain.Basket;

import java.util.List;

/**
 * Immutable configuration delivered to both clients before they signal MATCH_READY.
 */
public record MatchPreparation(GameMatch match, long hostPlayerId, long guestPlayerId, List<FruitSpawn> spawns,
                               List<Fruit> fruits, List<Basket> baskets, List<Integer> targetFruitIds) {
    public MatchPreparation {
        spawns = List.copyOf(spawns);
        fruits = List.copyOf(fruits);
        baskets = List.copyOf(baskets);
        targetFruitIds = List.copyOf(targetFruitIds);
    }
}
