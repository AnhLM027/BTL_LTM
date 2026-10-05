package Server.network;

import java.util.Collection;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import Server.model.enums.AccountRole;

public final class SessionRegistry {
    private final ConcurrentHashMap<String, ClientSession> bySessionId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ClientSession> byPlayerId = new ConcurrentHashMap<>();

    public void add(ClientSession session) {
        bySessionId.put(session.sessionId(), session);
    }

    public void bindPlayer(ClientSession session, long playerId, AccountRole role) {
        ClientSession previous = byPlayerId.put(playerId, session);
        session.bindPlayer(playerId);
        session.bindRole(role);
        if (previous != null && previous != session) {
            previous.close();
        }
    }

    public Optional<ClientSession> findPlayer(long playerId) {
        return Optional.ofNullable(byPlayerId.get(playerId)).filter(ClientSession::isOpen);
    }

    public Set<Long> onlinePlayerIds() {
        return Set.copyOf(byPlayerId.keySet());
    }

    public Collection<ClientSession> sessions() {
        return bySessionId.values();
    }

    public void remove(ClientSession session) {
        bySessionId.remove(session.sessionId(), session);
        Long playerId = session.playerId();
        if (playerId != null) {
            byPlayerId.remove(playerId, session);
        }
    }

    public void unbindPlayer(ClientSession session) {
        Long playerId = session.playerId();
        if (playerId != null) {
            byPlayerId.remove(playerId, session);
            session.clearPlayer();
        }
    }
}
