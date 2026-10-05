package Server.network;

import Server.model.domain.Player;
import Server.model.domain.Invite;
import Server.model.domain.MatchRoom;
import Server.model.enums.GameModeCode;
import Server.model.enums.AccountRole;
import Server.model.enums.PlayerState;
import Server.repository.AuthenticatedPlayer;
import Server.repository.PlayerRepository;
import Server.repository.RoomRepository;
import Server.repository.HistoryRepository;
import Server.repository.FruitRepository;
import Server.repository.GameModeRepository;
import Server.service.AuthService;
import Server.service.AuthenticationException;
import Server.service.RoomException;
import Server.service.RoomService;
import Server.service.MatchService;
import Server.service.MatchPreparation;
import Server.service.MatchStartResult;
import Server.service.MatchFinalization;
import Server.service.ScoreUpdate;
import Server.util.ServerLog;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Handles authenticated TCP messages that are available before room and match modules exist.
 */
public final class GameServerEventHandler implements ServerEventHandler, AutoCloseable {
    private final SessionRegistry sessions;
    private final AuthService authService;
    private final PlayerRepository playerRepository;
    private final RoomService roomService;
    private final MatchService matchService;
    private final HistoryRepository historyRepository;
    private final FruitRepository fruitRepository = new FruitRepository();
    private final GameModeRepository gameModeRepository = new GameModeRepository();
    private final RoomRepository roomRepository = new RoomRepository();
    private final ScheduledExecutorService matchScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> disconnectGrace = new ConcurrentHashMap<>();

    public GameServerEventHandler(SessionRegistry sessions, AuthService authService, PlayerRepository playerRepository,
                                  RoomService roomService, MatchService matchService) {
        this(sessions, authService, playerRepository, roomService, matchService, new HistoryRepository());
    }

    public GameServerEventHandler(SessionRegistry sessions, AuthService authService, PlayerRepository playerRepository,
                                  RoomService roomService, MatchService matchService, HistoryRepository historyRepository) {
        this.sessions = sessions;
        this.authService = authService;
        this.playerRepository = playerRepository;
        this.roomService = roomService;
        this.matchService = matchService;
        this.historyRepository = historyRepository;
    }

    @Override
    public void onConnected(ClientSession session) throws IOException {
        ServerLog.info("Session opened: " + session.sessionId());
        session.send(new ProtocolMessage("CONNECTED", Map.of("sessionId", session.sessionId())));
    }

    @Override
    public void onMessage(ClientSession session, ProtocolMessage message) throws IOException {
        try {
            ServerLog.info("Request received: type=" + message.type() + ", session=" + session.sessionId() + ", player=" + session.playerId());
            switch (message.type()) {
                case "PING" -> session.send(ProtocolMessage.of("PONG"));
                case "LOGIN" -> login(session, message);
                case "REGISTER" -> register(session, message);
                case "LOGOUT" -> logout(session);
                case "GET_ONLINE_PLAYERS" -> sendOnlinePlayers(session);
                case "GET_ROOM_SNAPSHOT" -> sendRoomSnapshot(session);
                case "GET_PROFILE" -> sendProfile(session, message);
                case "GET_FRUIT_CATALOG" -> sendFruitCatalog(session);
                case "GET_GAME_MODES" -> sendGameModes(session);
                case "GET_LEADERBOARD" -> sendLeaderboard(session);
                case "GET_MATCH_HISTORY" -> sendHistory(session);
                case "ADMIN_LIST_ACCOUNTS" -> sendAdminAccounts(session);
                case "ADMIN_CREATE_ACCOUNT" -> createAdminAccount(session, message);
                case "ADMIN_DELETE_ACCOUNT" -> deleteAdminAccount(session, message);
                case "ADMIN_UPDATE_ACCOUNT" -> updateAdminAccount(session, message);
                case "CREATE_ROOM" -> createRoom(session, message);
                case "LEAVE_ROOM" -> leaveRoom(session, message);
                case "CHANGE_GAME_MODE" -> changeGameMode(session, message);
                case "INVITE_PLAYER" -> invitePlayer(session, message);
                case "INVITE_ACCEPT" -> acceptInvite(session, message);
                case "INVITE_REJECT" -> rejectInvite(session, message);
                case "START_MATCH" -> startMatch(session, message);
                case "MATCH_READY" -> matchReady(session, message);
                case "CATCH_EVENT" -> catchEvent(session, message);
                default ->
                        session.send(ProtocolMessage.error("UNKNOWN_MESSAGE", "Unsupported message type: " + message.type()));
            }
        } catch (ProtocolException exception) {
            ServerLog.warning("Request rejected: type=" + message.type() + ", reason=" + exception.getMessage());
            session.send(ProtocolMessage.error("INVALID_MESSAGE", exception.getMessage()));
        } catch (AuthenticationException exception) {
            ServerLog.warning("Authentication rejected: type=" + message.type() + ", reason=" + exception.getMessage());
            session.send(new ProtocolMessage("LOGIN_FAILED", Map.of("reason", exception.getMessage())));
        } catch (RoomException exception) {
            ServerLog.warning("Room or match action rejected: type=" + message.type() + ", player=" + session.playerId() + ", reason=" + exception.getMessage());
            session.send(ProtocolMessage.error("ROOM_ACTION_REJECTED", exception.getMessage()));
        } catch (SQLException exception) {
            ServerLog.error("Database error while handling " + message.type(), exception);
            session.send(ProtocolMessage.error("DATABASE_UNAVAILABLE", "The server cannot access the database"));
        }
    }

    @Override
    public void onDisconnected(ClientSession session) {
        if (session.playerId() != null && session.role() == AccountRole.PLAYER) {
            ServerLog.info("Player disconnected: player=" + session.playerId() + "; reconnect grace is 30 seconds");
            notifyOpponent(session.playerId(), "OPPONENT_DISCONNECTED");
            scheduleDisconnectGrace(session.playerId());
            broadcastOnlinePlayers();
        }
    }

    private void login(ClientSession session, ProtocolMessage message)
            throws ProtocolException, AuthenticationException, SQLException, IOException, RoomException {
        if (session.playerId() != null) {
            session.send(ProtocolMessage.error("ALREADY_AUTHENTICATED", "Logout before signing in again"));
            return;
        }
        AuthenticatedPlayer authenticated = authService.authenticate(
                message.requiredField("username"),
                message.requiredField("password")
        );
        // SessionRegistry is keyed by player id.  Keep account-only admins out
        // of that id space so an admin can never collide with a real player.
        long identityId = authenticated.player() == null
                ? -authenticated.account().accountId()
                : authenticated.player().playerId();
        sessions.bindPlayer(session, identityId, authenticated.account().role());
        ServerLog.info("Login succeeded: account=" + authenticated.account().accountId()
                + ", player=" + (authenticated.player() == null ? "none" : authenticated.player().playerId())
                + ", role=" + authenticated.account().role());
        if (authenticated.player() != null) {
            notifyOpponent(authenticated.player().playerId(), "OPPONENT_RECONNECTED");
            ScheduledFuture<?> grace = disconnectGrace.remove(authenticated.player().playerId());
            if (grace != null) grace.cancel(false);
        }
        String displayName = authenticated.player() == null
                ? authenticated.account().username()
                : authenticated.player().displayName();
        session.send(new ProtocolMessage("LOGIN_SUCCESS", Map.of(
                "playerId", Long.toString(identityId),
                "displayName", displayName,
                "role", authenticated.account().role().name()
        )));
        if (authenticated.player() != null) sendRejoin(session, authenticated.player().playerId());
        broadcastOnlinePlayers();
    }

    private void sendFruitCatalog(ClientSession session) throws SQLException, IOException, AuthenticationException, ProtocolException {
        requireAuthenticated(session);
        List<FruitRepository.CatalogEntry> entries = fruitRepository.findCatalog();
        session.send(new ProtocolMessage("FRUIT_CATALOG_BEGIN", Map.of("count", Integer.toString(entries.size()))));
        for (FruitRepository.CatalogEntry entry : entries) {
            session.send(new ProtocolMessage("FRUIT_CATALOG_ITEM", Map.of(
                    "fruitId", Integer.toString(entry.fruitId()),
                    "groupId", Integer.toString(entry.groupId()),
                    "groupCode", entry.groupCode(),
                    "groupName", entry.groupName(),
                    "fruitCode", entry.fruitCode(),
                    "fruitName", entry.fruitName(),
                    "description", entry.description() == null ? "" : entry.description(),
                    "assetPath", entry.assetPath() == null ? "" : entry.assetPath(),
                    "nutritionLabels", entry.nutritionLabels() == null ? "" : entry.nutritionLabels()
            )));
        }
        session.send(new ProtocolMessage("FRUIT_CATALOG_END", Map.of()));
    }

    private void sendGameModes(ClientSession session) throws SQLException, IOException, AuthenticationException, ProtocolException {
        requireAuthenticated(session);
        List<Server.model.domain.GameMode> modes = gameModeRepository.findActive();
        session.send(new ProtocolMessage("GAME_MODES_BEGIN", Map.of("count", Integer.toString(modes.size()))));
        for (Server.model.domain.GameMode mode : modes) {
            session.send(new ProtocolMessage("GAME_MODE_ITEM", Map.of(
                    "modeCode", mode.modeCode().name(),
                    "modeName", mode.modeName(),
                    "description", mode.description() == null ? "" : mode.description())));
        }
        session.send(new ProtocolMessage("GAME_MODES_END", Map.of()));
    }

    private void register(ClientSession session, ProtocolMessage message) throws ProtocolException, AuthenticationException, SQLException, IOException {
        if (session.playerId() != null) {
            session.send(ProtocolMessage.error("ALREADY_AUTHENTICATED", "Logout before registering"));
            return;
        }
        AuthenticatedPlayer created = authService.register(message.requiredField("username"), message.requiredField("password"));
        ServerLog.info("Registration succeeded: player=" + created.player().playerId());
        session.send(new ProtocolMessage("REGISTER_SUCCESS", Map.of("playerId", Long.toString(created.player().playerId()), "displayName", created.player().displayName())));
    }

    private void logout(ClientSession session) throws IOException {
        if (session.playerId() == null) {
            session.send(ProtocolMessage.error("AUTHENTICATION_REQUIRED", "Login is required"));
            return;
        }
        long playerId = session.playerId();
        sessions.unbindPlayer(session);
        ServerLog.info("Logout succeeded: player=" + playerId);
        session.send(ProtocolMessage.of("LOGOUT_SUCCESS"));
        broadcastOnlinePlayers();
    }

    private void sendOnlinePlayers(ClientSession recipient) throws IOException, SQLException {
        if (recipient.playerId() == null) {
            recipient.send(ProtocolMessage.error("AUTHENTICATION_REQUIRED", "Login is required"));
            return;
        }
        sendOnlinePlayers(recipient, playerRepository.findByIds(sessions.onlinePlayerIds()));
    }

    private void sendRoomSnapshot(ClientSession session) throws IOException, ProtocolException, SQLException {
        long playerId = requirePlayer(session);
        try (var connection = Server.config.DatabaseConfig.openConnection()) {
            roomRepository.findActiveForPlayer(connection, playerId).ifPresent(room -> {
                try {
                    session.send(roomMessage(room));
                } catch (IOException ignored) {
                    // Normal disconnect cleanup handles the session.
                }
            });
        }
    }

    private void broadcastOnlinePlayers() {
        try {
            List<Player> players = playerRepository.findByIds(sessions.onlinePlayerIds());
            for (ClientSession session : sessions.sessions()) {
                if (session.playerId() != null && session.isOpen()) {
                    sendOnlinePlayers(session, players);
                }
            }
        } catch (SQLException exception) {
            System.err.println("Cannot broadcast online players: " + exception.getMessage());
        } catch (IOException exception) {
            // A recipient disconnected during broadcast; normal connection cleanup will remove it.
        }
    }

    private void sendOnlinePlayers(ClientSession recipient, List<Player> players) throws IOException, SQLException {
        recipient.send(new ProtocolMessage("ONLINE_PLAYERS_BEGIN", Map.of("count", Integer.toString(players.size()))));
        for (Player player : players) {
            recipient.send(new ProtocolMessage("ONLINE_PLAYER", Map.of(
                    "playerId", Long.toString(player.playerId()),
                    "displayName", player.displayName(),
                    "totalScore", Integer.toString(player.totalScore()),
                    "totalGames", Integer.toString(player.totalGames()),
                    "totalWins", Integer.toString(player.totalWins()),
                    "status", onlinePlayerState(player.playerId()).name()
            )));
        }
        recipient.send(ProtocolMessage.of("ONLINE_PLAYERS_END"));
    }

    private PlayerState onlinePlayerState(long playerId) throws SQLException {
        try (var connection = Server.config.DatabaseConfig.openConnection()) {
            return roomRepository.activeStateForPlayer(connection, playerId)
                    .orElse(PlayerState.ONLINE);
        }
    }

    private void sendProfile(ClientSession session, ProtocolMessage message) throws IOException, ProtocolException, SQLException {
        long requested = message.fields().containsKey("playerId") ? parseLong(message.requiredField("playerId"), "playerId") : requirePlayer(session);
        Player player = playerRepository.findById(requested).orElseThrow(() -> new ProtocolException("Player was not found"));
        session.send(new ProtocolMessage("PROFILE_DATA", playerFields(player)));
    }

    private void sendLeaderboard(ClientSession session) throws IOException, ProtocolException, SQLException {
        requireAuthenticated(session);
        List<Player> players = playerRepository.leaderboard(100);
        session.send(new ProtocolMessage("LEADERBOARD_BEGIN", Map.of("count", Integer.toString(players.size()))));
        for (Player player : players) session.send(new ProtocolMessage("LEADERBOARD_PLAYER", playerFields(player)));
        session.send(ProtocolMessage.of("LEADERBOARD_END"));
    }

    private void sendHistory(ClientSession session) throws IOException, ProtocolException, SQLException {
        long playerId = requirePlayer(session);
        var entries = historyRepository.findForPlayer(playerId);
        session.send(new ProtocolMessage("MATCH_HISTORY_BEGIN", Map.of("count", Integer.toString(entries.size()))));
        for (var entry : entries)
            session.send(new ProtocolMessage("MATCH_HISTORY_ITEM", Map.of(
                    "matchId", Long.toString(entry.matchId()), "modeCode", entry.modeCode(), "startedAt", String.valueOf(entry.startedAt()),
                    "endedAt", String.valueOf(entry.endedAt()), "opponentPlayerId", Long.toString(entry.opponentPlayerId()),
                    "opponentName", entry.opponentName(), "score", Integer.toString(entry.score()), "correctCount", Integer.toString(entry.correctCount()),
                    "wrongCount", Integer.toString(entry.wrongCount()), "result", entry.result())));
        session.send(ProtocolMessage.of("MATCH_HISTORY_END"));
    }

    private Map<String, String> playerFields(Player player) {
        return Map.of("playerId", Long.toString(player.playerId()), "displayName", player.displayName(), "totalScore", Integer.toString(player.totalScore()),
                "totalGames", Integer.toString(player.totalGames()), "totalWins", Integer.toString(player.totalWins()));
    }

    private void sendAdminAccounts(ClientSession session) throws IOException, ProtocolException, SQLException {
        requireAdmin(session);
        var accounts = authService.publicAccounts();
        session.send(new ProtocolMessage("ADMIN_ACCOUNTS_BEGIN", Map.of("count", Integer.toString(accounts.size()))));
        for (var account : accounts)
            session.send(new ProtocolMessage("ADMIN_ACCOUNT", Map.of(
                    "accountId", Long.toString(account.accountId()), "username", account.username(),
                    "role", account.role().name(), "status", account.status().name())));
        session.send(ProtocolMessage.of("ADMIN_ACCOUNTS_END"));
    }

    private void deleteAdminAccount(ClientSession session, ProtocolMessage message) throws IOException, ProtocolException, SQLException {
        requireAdmin(session);
        long accountId = parseLong(message.requiredField("accountId"), "accountId");
        authService.deleteAccount(accountId);
        session.send(ProtocolMessage.of("ADMIN_ACCOUNT_DELETED"));
    }

    private void createAdminAccount(ClientSession session, ProtocolMessage message) throws IOException, ProtocolException, SQLException, AuthenticationException {
        requireAdmin(session);
        authService.createAdminAccount(message.requiredField("username"), message.requiredField("password"), message.requiredField("role"));
        session.send(ProtocolMessage.of("ADMIN_ACCOUNT_CREATED"));
    }

    private void updateAdminAccount(ClientSession session, ProtocolMessage message) throws IOException, ProtocolException, SQLException, AuthenticationException {
        requireAdmin(session);
        authService.updateAccount(parseLong(message.requiredField("accountId"), "accountId"), message.requiredField("username"), message.requiredField("role"), message.requiredField("status"), message.fields().get("password"));
        session.send(ProtocolMessage.of("ADMIN_ACCOUNT_UPDATED"));
    }

    private void createRoom(ClientSession session, ProtocolMessage message)
            throws IOException, ProtocolException, SQLException, RoomException {
        MatchRoom room = roomService.createRoom(requirePlayer(session), parseMode(message.requiredField("modeCode")));
        ServerLog.info("Room created: room=" + room.roomId() + ", host=" + room.hostPlayerId() + ", mode=" + room.modeId());
        session.send(new ProtocolMessage("ROOM_CREATED", Map.of("roomId", Long.toString(room.roomId()))));
        sendRoomUpdated(room);
    }

    private void changeGameMode(ClientSession session, ProtocolMessage message)
            throws IOException, ProtocolException, SQLException, RoomException {
        MatchRoom room = roomService.changeGameMode(
                requirePlayer(session),
                parseLong(message.requiredField("roomId"), "roomId"),
                parseMode(message.requiredField("modeCode"))
        );
        sendRoomUpdated(room);
    }

    private void leaveRoom(ClientSession session, ProtocolMessage message)
            throws IOException, ProtocolException, SQLException, RoomException {
        long playerId = requirePlayer(session);
        long roomId = parseLong(message.requiredField("roomId"), "roomId");

        RoomService.LeaveRoomOutcome outcome = roomService.leaveRoom(playerId, roomId);
        ServerLog.info("Leave room: room=" + roomId + ", player=" + playerId + ", isHost=" + outcome.isHost());

        session.send(new ProtocolMessage("ROOM_LEFT", Map.of(
                "roomId", Long.toString(roomId),
                "reason", outcome.isHost() ? "HOST_LEFT" : "GUEST_LEFT"
        )));

        if (outcome.isHost()) {
            if (outcome.guestPlayerId() != null) {
                sessions.findPlayer(outcome.guestPlayerId()).ifPresent(guest -> {
                    try {
                        guest.send(new ProtocolMessage("ROOM_LEFT", Map.of(
                                "roomId", Long.toString(roomId),
                                "reason", "HOST_CANCELLED"
                        )));
                    } catch (IOException ignored) {}
                });
            }
        } else {
            // If the host is no longer connected, the guest leaving means the
            // disconnected host has effectively left as well. Do not leave a
            // stale WAITING room that blocks the host on the next login.
            boolean disconnectedHost = outcome.hostPlayerId() != null
                    && sessions.findPlayer(outcome.hostPlayerId()).isEmpty();
            if (disconnectedHost) {
                roomService.cancelRoom(roomId);
                ServerLog.info("Cancelled stale room after guest left: room=" + roomId
                        + ", disconnectedHost=" + outcome.hostPlayerId());
            } else if (outcome.room().isPresent()) {
                sendRoomUpdated(outcome.room().get());
            }
        }
    }

    private void invitePlayer(ClientSession session, ProtocolMessage message)
            throws IOException, ProtocolException, SQLException, RoomException {
        long senderPlayerId = requirePlayer(session);
        long receiverPlayerId = parseLong(message.requiredField("playerId"), "playerId");
        if (sessions.findPlayer(receiverPlayerId).isEmpty()) {
            throw new RoomException("Invited player is offline");
        }
        Invite invite = roomService.createInvite(
                senderPlayerId,
                parseLong(message.requiredField("roomId"), "roomId"),
                receiverPlayerId
        );
        ServerLog.info("Invite created: invite=" + invite.inviteId() + ", sender=" + senderPlayerId + ", receiver=" + receiverPlayerId);
        session.send(new ProtocolMessage("INVITE_SENT", Map.of(
                "inviteId", Long.toString(invite.inviteId()),
                "expiresAt", invite.expiresAt().toString()
        )));
        sessions.findPlayer(receiverPlayerId).ifPresent(recipient -> sendInviteNotification(recipient, invite));
    }

    private void acceptInvite(ClientSession session, ProtocolMessage message)
            throws IOException, ProtocolException, SQLException, RoomException {
        MatchRoom room = roomService.acceptInvite(
                requirePlayer(session),
                parseLong(message.requiredField("inviteId"), "inviteId")
        );
        ServerLog.info("Invite accepted: room=" + room.roomId() + ", guest=" + session.playerId());
        sendRoomUpdated(room);
    }

    private void rejectInvite(ClientSession session, ProtocolMessage message)
            throws IOException, ProtocolException, SQLException, RoomException {
        Invite invite = roomService.rejectInvite(
                requirePlayer(session),
                parseLong(message.requiredField("inviteId"), "inviteId")
        );
        ServerLog.info("Invite rejected: invite=" + invite.inviteId() + ", player=" + session.playerId());
        session.send(new ProtocolMessage("INVITE_RESULT", Map.of(
                "inviteId", Long.toString(invite.inviteId()),
                "state", invite.state().name()
        )));
        sessions.findPlayer(invite.senderPlayerId()).ifPresent(sender -> {
            try {
                sender.send(new ProtocolMessage("INVITE_RESULT", Map.of(
                        "inviteId", Long.toString(invite.inviteId()),
                        "state", invite.state().name()
                )));
            } catch (IOException ignored) {
                // Disconnection cleanup removes this session.
            }
        });
    }

    private void sendInviteNotification(ClientSession recipient, Invite invite) {
        try {
            recipient.send(new ProtocolMessage("INVITE_NOTIFICATION", Map.of(
                    "inviteId", Long.toString(invite.inviteId()),
                    "roomId", Long.toString(invite.roomId()),
                    "senderPlayerId", Long.toString(invite.senderPlayerId()),
                    "expiresAt", invite.expiresAt().toString()
            )));
        } catch (IOException ignored) {
            // A client may go offline between the online check and delivery.
        }
    }

    private void sendRoomUpdated(MatchRoom room) throws IOException {
        ProtocolMessage message = roomMessage(room);
        ClientSession host = sessions.findPlayer(room.hostPlayerId()).orElse(null);
        if (host != null) {
            host.send(message);
        }
        if (room.guestPlayerId() != null) {
            ClientSession guest = sessions.findPlayer(room.guestPlayerId()).orElse(null);
            if (guest != null && guest != host) {
                guest.send(message);
            }
        }
    }

    private ProtocolMessage roomMessage(MatchRoom room) {
        return new ProtocolMessage("ROOM_UPDATED", Map.of(
                "roomId", Long.toString(room.roomId()),
                "hostPlayerId", Long.toString(room.hostPlayerId()),
                "guestPlayerId", room.guestPlayerId() == null ? "" : Long.toString(room.guestPlayerId()),
                "modeId", Integer.toString(room.modeId()),
                "roomState", room.state().name(),
                "hostReady", Boolean.toString(room.hostReady()),
                "guestReady", Boolean.toString(room.guestReady())
        ));
    }

    private long requireAuthenticated(ClientSession session) throws ProtocolException {
        if (session.playerId() == null) {
            throw new ProtocolException("Login is required");
        }
        return session.playerId();
    }

    private long requirePlayer(ClientSession session) throws ProtocolException {
        long identityId = requireAuthenticated(session);
        if (session.role() != AccountRole.PLAYER) {
            throw new ProtocolException("Player account is required");
        }
        return identityId;
    }

    private void requireAdmin(ClientSession session) throws ProtocolException {
        requireAuthenticated(session);
        if (session.role() != AccountRole.ADMIN) throw new ProtocolException("Administrator role is required");
    }

    private GameModeCode parseMode(String value) throws ProtocolException {
        try {
            return GameModeCode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ProtocolException("Unknown game mode: " + value);
        }
    }

    private long parseLong(String value, String fieldName) throws ProtocolException {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new ProtocolException("Invalid " + fieldName);
        }
    }

    private void startMatch(ClientSession session, ProtocolMessage message) throws IOException, ProtocolException, SQLException, RoomException {
        MatchPreparation prep = matchService.prepare(requirePlayer(session), parseLong(message.requiredField("roomId"), "roomId"));
        ServerLog.info("Match prepared: match=" + prep.match().matchId() + ", room=" + prep.match().roomId()
                + ", players=" + prep.hostPlayerId() + "/" + prep.guestPlayerId() + ", spawns=" + prep.spawns().size());
        java.util.LinkedHashMap<String, String> preparationFields = new java.util.LinkedHashMap<>();
        preparationFields.put("matchId", Long.toString(prep.match().matchId()));
        preparationFields.put("modeId", Integer.toString(prep.match().modeId()));
        preparationFields.put("seed", Long.toString(prep.match().seed()));
        preparationFields.put("durationSeconds", "30");
        if (prep.missionLabel() != null) {
            preparationFields.put("missionLabelId", Integer.toString(prep.missionLabel().labelId()));
            preparationFields.put("missionLabelCode", prep.missionLabel().labelCode());
            preparationFields.put("missionLabelName", prep.missionLabel().displayName());
        }
        ProtocolMessage header = new ProtocolMessage("MATCH_PREPARE", preparationFields);
        for (long id : List.of(prep.hostPlayerId(), prep.guestPlayerId()))
            sessions.findPlayer(id).ifPresent(target -> {
                try {
                    target.send(header);
                    for (var fruit : prep.fruits())
                        target.send(new ProtocolMessage("FRUIT_CONFIG", Map.of("fruitId", Integer.toString(fruit.fruitId()), "groupId", Integer.toString(fruit.groupId()), "fruitCode", fruit.fruitCode(), "fruitName", fruit.fruitName(), "assetPath", fruit.defaultAssetPath() == null ? "" : fruit.defaultAssetPath())));
                    for (var basket : prep.baskets())
                        target.send(new ProtocolMessage("BASKET_CONFIG", Map.of("basketId", Integer.toString(basket.basketId()), "groupId", Integer.toString(basket.groupId()), "basketName", basket.basketName(), "assetPath", basket.assetPath() == null ? "" : basket.assetPath())));
                    for (var spawn : prep.spawns())
                        target.send(new ProtocolMessage("FRUIT_SPAWN", Map.of("matchId", Long.toString(prep.match().matchId()), "fruitInstanceId", Long.toString(spawn.fruitInstanceId()), "fruitId", Integer.toString(spawn.fruitId()), "spawnOffsetMs", Long.toString(spawn.spawnOffsetMs()), "xPosition", Integer.toString(spawn.xPosition()))));
                    target.send(new ProtocolMessage("MATCH_PREPARE_END", Map.of("matchId", Long.toString(prep.match().matchId()))));
                } catch (IOException ignored) {
                }
            });
    }

    private void sendRejoin(ClientSession session, long playerId) throws SQLException, IOException, RoomException {
        var rejoin = matchService.rejoinPreparation(playerId);
        if (rejoin.isEmpty()) return;
        var prep = rejoin.get();
        ServerLog.info("Match rejoin snapshot sent: match=" + prep.match().matchId() + ", player=" + playerId);
        long remaining = Math.max(0, prep.match().durationSeconds() * 1000L - (System.currentTimeMillis() - prep.match().startedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));
        if (remaining == 0) {
            // A scheduler may have been interrupted while the server was
            // restarting. Never re-open an expired match as PLAYING.
            MatchFinalization finalization = matchService.finalizeMatch(prep.match().matchId());
            for (Server.model.domain.MatchPlayer player : finalization.players()) {
                if (player.playerId() == playerId) {
                    sendMatchResult(session, finalization, player);
                } else {
                    sessions.findPlayer(player.playerId()).ifPresent(target -> {
                        try {
                            sendMatchResult(target, finalization, player);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
            return;
        }
        session.send(new ProtocolMessage("MATCH_REJOIN", Map.of("matchId", Long.toString(prep.match().matchId()), "remainingMs", Long.toString(remaining))));
        for (var fruit : prep.fruits())
            session.send(new ProtocolMessage("FRUIT_CONFIG", Map.of("fruitId", Integer.toString(fruit.fruitId()), "groupId", Integer.toString(fruit.groupId()), "fruitCode", fruit.fruitCode(), "fruitName", fruit.fruitName(), "assetPath", fruit.defaultAssetPath() == null ? "" : fruit.defaultAssetPath())));
        for (var basket : prep.baskets())
            session.send(new ProtocolMessage("BASKET_CONFIG", Map.of("basketId", Integer.toString(basket.basketId()), "groupId", Integer.toString(basket.groupId()), "basketName", basket.basketName(), "assetPath", basket.assetPath() == null ? "" : basket.assetPath())));
        for (var spawn : prep.spawns())
            session.send(new ProtocolMessage("FRUIT_SPAWN", Map.of("matchId", Long.toString(prep.match().matchId()), "fruitInstanceId", Long.toString(spawn.fruitInstanceId()), "fruitId", Integer.toString(spawn.fruitId()), "spawnOffsetMs", Long.toString(spawn.spawnOffsetMs()), "xPosition", Integer.toString(spawn.xPosition()))));
        session.send(new ProtocolMessage("MATCH_START", Map.of("matchId", Long.toString(prep.match().matchId()), "startedAt", prep.match().startedAt().toString(), "durationSeconds", Integer.toString(prep.match().durationSeconds()))));
        for (var score : matchService.scores(prep.match().matchId()))
            session.send(new ProtocolMessage("SCORE_UPDATE", Map.of("matchId", Long.toString(prep.match().matchId()), "playerId", Long.toString(score.playerId()), "score", Integer.toString(score.finalScore()), "correctCount", Integer.toString(score.correctCount()), "wrongCount", Integer.toString(score.wrongCount()))));
    }

    private void sendMatchResult(ClientSession target, MatchFinalization finalization,
                                 Server.model.domain.MatchPlayer player) throws IOException {
        Server.model.domain.MatchPlayer opponent = finalization.players().stream()
                .filter(candidate -> candidate.playerId() != player.playerId())
                .findFirst().orElseThrow();
        target.send(new ProtocolMessage("MATCH_RESULT", Map.of(
                "matchId", Long.toString(finalization.matchId()),
                "playerId", Long.toString(player.playerId()),
                "score", Integer.toString(player.finalScore()),
                "correctCount", Integer.toString(player.correctCount()),
                "wrongCount", Integer.toString(player.wrongCount()),
                "result", player.result().name(),
                "opponentPlayerId", Long.toString(opponent.playerId()),
                "opponentScore", Integer.toString(opponent.finalScore()),
                "opponentResult", opponent.result().name()
        )));
    }

    private void matchReady(ClientSession session, ProtocolMessage message) throws IOException, ProtocolException, SQLException, RoomException {
        MatchStartResult result = matchService.ready(requirePlayer(session), parseLong(message.requiredField("matchId"), "matchId"));
        ServerLog.info("Match ready: match=" + result.match().matchId() + ", player=" + session.playerId());
        session.send(ProtocolMessage.of("MATCH_READY_ACK"));
        if (result.started()) {
            ServerLog.info("Match started: match=" + result.match().matchId() + ", duration=" + result.match().durationSeconds() + "s");
            ProtocolMessage start = new ProtocolMessage("MATCH_START", Map.of("matchId", Long.toString(result.match().matchId()), "startedAt", result.match().startedAt().toString(), "durationSeconds", "30"));
            for (long playerId : List.of(result.hostPlayerId(), result.guestPlayerId()))
                sessions.findPlayer(playerId).ifPresent(target -> {
                    try {
                        target.send(start);
                    } catch (IOException ignored) {
                    }
                });
            scheduleFinalization(result.match().matchId(), result.match().durationSeconds());
        }
    }

    private void catchEvent(ClientSession session, ProtocolMessage message) throws IOException, ProtocolException, SQLException, RoomException {
        ScoreUpdate score = matchService.catchFruit(requirePlayer(session), parseLong(message.requiredField("matchId"), "matchId"), parseLong(message.requiredField("fruitInstanceId"), "fruitInstanceId"), Integer.parseInt(message.requiredField("basketId")));
        ServerLog.info("Catch accepted: match=" + score.matchId() + ", player=" + score.playerId()
                + ", fruitInstance=" + message.requiredField("fruitInstanceId") + ", score=" + score.score());
        session.send(new ProtocolMessage("CATCH_ACK", Map.of("matchId", Long.toString(score.matchId()), "fruitInstanceId", message.requiredField("fruitInstanceId"), "accepted", "true")));
        ProtocolMessage update = new ProtocolMessage("SCORE_UPDATE", Map.of("matchId", Long.toString(score.matchId()), "playerId", Long.toString(score.playerId()), "score", Integer.toString(score.score()), "correctCount", Integer.toString(score.correctCount()), "wrongCount", Integer.toString(score.wrongCount())));
        for (long playerId : matchService.participants(score.matchId()))
            sessions.findPlayer(playerId).ifPresent(target -> {
                try {
                    target.send(update);
                } catch (IOException ignored) {
                }
            });
    }

    private void scheduleFinalization(long matchId, int durationSeconds) {
        ServerLog.info("Match finalization scheduled: match=" + matchId + ", in=" + durationSeconds + "s");
        matchScheduler.schedule(() -> {
            try {
                MatchFinalization finalization = matchService.finalizeMatch(matchId);
                ServerLog.info("Match finalized: match=" + finalization.matchId());
                for (Server.model.domain.MatchPlayer player : finalization.players()) {
                    Server.model.domain.MatchPlayer opponent = finalization.players().stream()
                            .filter(candidate -> candidate.playerId() != player.playerId()).findFirst().orElseThrow();
                    sessions.findPlayer(player.playerId()).ifPresent(target -> {
                        try {
                            target.send(new ProtocolMessage("MATCH_RESULT", Map.of(
                                    "matchId", Long.toString(finalization.matchId()),
                                    "playerId", Long.toString(player.playerId()),
                                    "score", Integer.toString(player.finalScore()),
                                    "correctCount", Integer.toString(player.correctCount()),
                                    "wrongCount", Integer.toString(player.wrongCount()),
                                    "result", player.result().name(),
                                    "opponentPlayerId", Long.toString(opponent.playerId()),
                                    "opponentScore", Integer.toString(opponent.finalScore()),
                                    "opponentResult", opponent.result().name()
                            )));
                        } catch (IOException ignored) {
                            // A reconnect can retrieve persisted history after the match.
                        }
                    });
                }
                broadcastOnlinePlayers();
            } catch (SQLException | RoomException exception) {
                ServerLog.error("Could not finalize match " + matchId, exception);
            }
        }, durationSeconds, TimeUnit.SECONDS);
    }

    public void recoverMatches() {
        try {
            for (var match : matchService.recoverableMatches()) {
                long remaining = match.startedAt() == null ? 0 : Math.max(0, match.durationSeconds() * 1000L - (System.currentTimeMillis() - match.startedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()));
                if (remaining == 0) scheduleFinalization(match.matchId(), 0);
                else
                    matchScheduler.schedule(() -> scheduleFinalization(match.matchId(), 0), remaining, TimeUnit.MILLISECONDS);
                ServerLog.info("Recovered active match: match=" + match.matchId() + ", remaining=" + remaining + "ms");
            }
        } catch (SQLException exception) {
            ServerLog.error("Cannot recover active matches", exception);
        }
    }

    private void notifyOpponent(long playerId, String type) {
        try {
            for (var match : matchService.recoverableMatches())
                for (long participant : matchService.participants(match.matchId()))
                    if (participant != playerId) sessions.findPlayer(participant).ifPresent(target -> {
                        try {
                            target.send(new ProtocolMessage(type, Map.of("playerId", Long.toString(playerId), "graceSeconds", "30")));
                        } catch (IOException ignored) {
                        }
                    });
        } catch (SQLException ignored) {
        }
    }

    private void scheduleDisconnectGrace(long playerId) {
        try {
            for (var match : matchService.recoverableMatches())
                if (matchService.participants(match.matchId()).contains(playerId))
                    disconnectGrace.put(playerId, matchScheduler.schedule(() -> scheduleFinalization(match.matchId(), 0), 30, TimeUnit.SECONDS));
            ServerLog.info("Reconnect grace scheduled: player=" + playerId + ", seconds=30");
        } catch (SQLException ignored) {
            ServerLog.warning("Could not schedule reconnect grace for player=" + playerId);
        }
    }

    @Override
    public void close() {
        ServerLog.info("Stopping match scheduler");
        matchScheduler.shutdownNow();
    }
}
