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
import Server.repository.NutritionLabelRepository;
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
    private final NutritionLabelRepository nutritionLabels;
    private final BasketRepository baskets;

    public MatchService(RoomRepository rooms, MatchRepository matches, FruitRepository fruits,
                        GameModeRepository modes, CatchEventRepository catches) {
        this(rooms, matches, fruits, modes, catches, new NutritionLabelRepository(), new BasketRepository());
    }

    MatchService(RoomRepository rooms, MatchRepository matches, FruitRepository fruits,
                 GameModeRepository modes, CatchEventRepository catches, NutritionLabelRepository nutritionLabels, BasketRepository baskets) {
        this.rooms = rooms;
        this.matches = matches;
        this.fruits = fruits;
        this.modes = modes;
        this.catches = catches;
        this.nutritionLabels = nutritionLabels;
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
            return java.util.Optional.of(new MatchPreparation(match.get(), room.hostPlayerId(), room.guestPlayerId(), matches.findSpawns(connection, match.get().matchId()), fruits.findActive(), baskets.findActive(), match.get().missionLabelId() == null ? null : nutritionLabels.findById(match.get().missionLabelId())));
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
                Integer missionLabelId = mode == GameModeCode.NUTRITION
                        ? nutritionLabels.findRandomActiveId(connection) : null;
                long seed = new Random().nextLong();
                Random random = new Random(seed);
                List<FruitSpawn> spawns = new ArrayList<>();
                for (int index = 0; index < 20; index++) {
                    Fruit fruit = catalog.get(random.nextInt(catalog.size()));
                    spawns.add(new FruitSpawn(0, 0, fruit.fruitId(), index, index * 1_400L,
                            50 + random.nextInt(701)));
                }
                GameMatch match = matches.create(connection, roomId, room.modeId(), missionLabelId,
                        seed, hostPlayerId, room.guestPlayerId(), spawns);
                List<FruitSpawn> persisted = matches.findSpawns(connection, match.matchId());
                connection.commit();
                return new MatchPreparation(match, room.hostPlayerId(), room.guestPlayerId(), persisted, catalog, baskets.findActive(), missionLabelId == null ? null : nutritionLabels.findById(missionLabelId));
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
}
