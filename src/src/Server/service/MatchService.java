package Server.service;

import Server.config.DatabaseConfig;
import Server.model.domain.Fruit;
import Server.model.domain.FruitSpawn;
import Server.model.domain.GameMatch;
import Server.model.domain.MatchPlayer;
import Server.model.domain.MatchRoom;
import Server.model.enums.GameModeCode;
import Server.model.enums.MatchResult;
import Server.model.enums.MatchState;
import Server.model.enums.RoomState;
import Server.repository.CatchEventRepository;
import Server.repository.BasketRepository;
import Server.repository.FruitRepository;
import Server.repository.GameModeRepository;
import Server.repository.MatchRepository;
import Server.repository.RoomRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Authoritative match lifecycle and scoring operations.
 */
public final class MatchService {
    private final RoomRepository rooms;
    private final MatchRepository matches;
    private final FruitRepository fruits;
    private final GameModeRepository modes;
    private final CatchEventRepository catches;
    private final BasketRepository baskets;

    public MatchService(RoomRepository rooms, MatchRepository matches, FruitRepository fruits,
                        GameModeRepository modes, CatchEventRepository catches) {
        this(rooms, matches, fruits, modes, catches, new BasketRepository());
    }

    MatchService(RoomRepository rooms, MatchRepository matches, FruitRepository fruits,
                 GameModeRepository modes, CatchEventRepository catches, BasketRepository baskets) {
        this.rooms = rooms;
        this.matches = matches;
        this.fruits = fruits;
        this.modes = modes;
        this.catches = catches;
        this.baskets = baskets;
    }

    public ScoreUpdate catchFruit(long playerId, long matchId, long fruitInstanceId, int basketId)
            throws SQLException, RoomException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                ScoreUpdate score = catches.process(connection, matchId, playerId, fruitInstanceId, basketId);
                connection.commit();
                return score;
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public ScoreUpdate applyComboBonus(ScoreUpdate score, int bonus, int comboCount) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                ScoreUpdate updated = catches.applyComboBonus(connection, score, bonus, comboCount);
                connection.commit();
                return updated;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public List<Long> participants(long matchId) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            return matches.findPlayerIds(connection, matchId);
        }
    }

    public List<GameMatch> recoverableMatches() throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            return matches.findRecoverable(connection);
        }
    }

    public java.util.Optional<MatchPreparation> rejoinPreparation(long playerId) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            var match = matches.findActiveForPlayer(connection, playerId);
            if (match.isEmpty()) return java.util.Optional.empty();
            MatchRoom room = rooms.findById(connection, match.get().roomId(), false).orElseThrow();
            return java.util.Optional.of(new MatchPreparation(match.get(), room.hostPlayerId(), room.guestPlayerId(),
                    matches.findSpawns(connection, match.get().matchId()), fruits.findActive(), baskets.findActive(),
                    matches.findTargetFruits(connection, match.get().matchId())));
        }
    }

    public List<MatchPlayer> scores(long matchId) throws SQLException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            return matches.findPlayers(connection, matchId, false);
        }
    }

    public MatchPreparation prepare(long hostPlayerId, long roomId) throws SQLException, RoomException {
        List<Fruit> catalog = fruits.findActive();
        if (catalog.isEmpty()) throw new RoomException("Fruit catalog is empty");
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                MatchRoom room = rooms.findById(connection, roomId, true)
                        .orElseThrow(() -> new RoomException("Room was not found"));
                if (room.hostPlayerId() != hostPlayerId || room.state() != RoomState.FULL || !room.hasGuest()) {
                    throw new RoomException("Only the full room host can start a match");
                }
                GameModeCode mode = modes.findById(room.modeId())
                        .orElseThrow(() -> new RoomException("Game mode was not found")).modeCode();
                long seed = new Random().nextLong();
                Random random = new Random(seed);
                List<Integer> targetFruitIds = mode == GameModeCode.ORDER ? chooseTargets(catalog, random) : List.of();
                List<FruitSpawn> spawns = mode == GameModeCode.CLASSIC
                        ? classicSpawns(catalog, random) : orderSpawns(catalog, targetFruitIds, random);
                GameMatch match = matches.create(connection, roomId, room.modeId(), seed, hostPlayerId,
                        room.guestPlayerId(), spawns, targetFruitIds);
                List<FruitSpawn> persisted = matches.findSpawns(connection, match.matchId());
                connection.commit();
                return new MatchPreparation(match, room.hostPlayerId(), room.guestPlayerId(), persisted, catalog,
                        baskets.findActive(), targetFruitIds);
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public MatchStartResult ready(long playerId, long matchId) throws SQLException, RoomException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                GameMatch match = matches.find(connection, matchId, true)
                        .orElseThrow(() -> new RoomException("Match was not found"));
                if (match.state() != MatchState.PREPARING) throw new RoomException("Match is not preparing");
                MatchRoom room = rooms.findById(connection, match.roomId(), true)
                        .orElseThrow(() -> new RoomException("Room was not found"));
                MatchRoom updated = rooms.markReady(connection, room, playerId);
                boolean started = updated.bothPlayersReady();
                if (started) {
                    matches.start(connection, matchId);
                    rooms.markPlaying(connection, room.roomId());
                    match = matches.find(connection, matchId, false).orElseThrow();
                }
                connection.commit();
                return new MatchStartResult(match, room.hostPlayerId(), room.guestPlayerId(), started);
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    /**
     * Finalizes once. A second caller observes the finished match and does no writes.
     */
    public MatchFinalization finalizeMatch(long matchId) throws SQLException, RoomException {
        try (Connection connection = DatabaseConfig.openConnection()) {
            connection.setAutoCommit(false);
            try {
                GameMatch match = matches.find(connection, matchId, true)
                        .orElseThrow(() -> new RoomException("Match was not found"));
                if (match.state() == MatchState.FINISHED) {
                    List<MatchPlayer> players = matches.findPlayers(connection, matchId, false);
                    connection.commit();
                    return new MatchFinalization(matchId, players);
                }
                if (match.state() != MatchState.PLAYING && match.state() != MatchState.FINALIZING)
                    throw new RoomException("Match is not playing");
                List<MatchPlayer> players = matches.findPlayers(connection, matchId, true);
                if (players.size() != 2) throw new SQLException("A match must have exactly two players");
                if (match.state() == MatchState.PLAYING) matches.markFinalizing(connection, matchId);
                MatchPlayer first = players.get(0);
                MatchPlayer second = players.get(1);
                MatchResult firstResult = outcome(first.finalScore(), second.finalScore());
                MatchResult secondResult = opposite(firstResult);
                matches.updatePlayerResult(connection, matchId, first.playerId(), firstResult);
                matches.updatePlayerResult(connection, matchId, second.playerId(), secondResult);
                matches.updatePlayerStatistics(connection, first, firstResult == MatchResult.WIN);
                matches.updatePlayerStatistics(connection, second, secondResult == MatchResult.WIN);
                Long winner = firstResult == MatchResult.WIN ? first.playerId()
                        : secondResult == MatchResult.WIN ? second.playerId() : null;
                matches.finish(connection, matchId, winner);
                rooms.markFinished(connection, match.roomId());
                List<MatchPlayer> finalized = matches.findPlayers(connection, matchId, false);
                connection.commit();
                return new MatchFinalization(matchId, finalized);
            } catch (SQLException | RoomException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private MatchResult outcome(int score, int opponentScore) {
        return score > opponentScore ? MatchResult.WIN : score < opponentScore ? MatchResult.LOSE : MatchResult.DRAW;
    }

    private MatchResult opposite(MatchResult result) {
        return result == MatchResult.WIN ? MatchResult.LOSE : result == MatchResult.LOSE ? MatchResult.WIN : MatchResult.DRAW;
    }

    private List<Integer> chooseTargets(List<Fruit> catalog, Random random) throws RoomException {
        if (catalog.size() < 3) throw new RoomException("ORDER requires at least three active fruits");
        List<Fruit> shuffled = new ArrayList<>(catalog);
        java.util.Collections.shuffle(shuffled, random);
        return shuffled.subList(0, 3).stream().map(Fruit::fruitId).toList();
    }

    private List<FruitSpawn> classicSpawns(List<Fruit> catalog, Random random) {
        java.util.Map<Integer, Integer> bombs = new java.util.HashMap<>();
        while (bombs.size() < 4) {
            int candidate = random.nextInt(20);
            long lateBombs = bombs.keySet().stream().filter(slot -> slot >= 15).count();
            if (candidate >= 15 && lateBombs >= 2) continue;
            if (bombs.keySet().stream().anyMatch(slot -> Math.abs(slot - candidate) < 2)) continue;
            int x = 50 + random.nextInt(701);
            boolean blocksSafeLane = bombs.entrySet().stream().anyMatch(existing ->
                    Math.abs(existing.getKey() - candidate) * 1_400L < 3_000L
                            && Math.abs(existing.getValue() - x) < 150);
            if (blocksSafeLane) continue;
            bombs.put(candidate, x);
        }
        List<FruitSpawn> spawns = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            long offset = index * 1_400L;
            boolean bomb = bombs.containsKey(index);
            Fruit fruit = bomb ? null : catalog.get(random.nextInt(catalog.size()));
            spawns.add(new FruitSpawn(0, 0, fruit == null ? null : fruit.fruitId(), bomb, index, offset,
                    bomb ? bombs.get(index) : 50 + random.nextInt(701), fallDuration(offset)));
        }
        return spawns;
    }

    private List<FruitSpawn> orderSpawns(List<Fruit> catalog, List<Integer> targets, Random random) {
        List<Integer> source = new ArrayList<>();
        for (int i = 0; i < 12; i++) source.add(targets.get(random.nextInt(targets.size())));
        List<Integer> nonTargets = catalog.stream().map(Fruit::fruitId).filter(id -> !targets.contains(id)).toList();
        for (int i = 0; i < 8; i++) source.add(nonTargets.isEmpty()
                ? targets.get(random.nextInt(targets.size())) : nonTargets.get(random.nextInt(nonTargets.size())));
        java.util.Collections.shuffle(source, random);
        List<FruitSpawn> spawns = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            long offset = index * 1_400L;
            spawns.add(new FruitSpawn(0, 0, source.get(index), false, index, offset,
                    50 + random.nextInt(701), fallDuration(offset)));
        }
        return spawns;
    }

    private int fallDuration(long spawnOffsetMs) {
        return spawnOffsetMs < 20_000 ? 4_000 : 2_500;
    }
}
