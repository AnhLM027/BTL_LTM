package Server.service;

import Server.model.domain.MatchPlayer;

import java.util.List;

/**
 * Immutable result returned only after the finalization transaction commits.
 */
public record MatchFinalization(long matchId, List<MatchPlayer> players) {
    public MatchFinalization {
        players = List.copyOf(players);
    }
}
