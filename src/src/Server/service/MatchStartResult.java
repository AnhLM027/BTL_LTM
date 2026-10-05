package Server.service;

import Server.model.domain.GameMatch;

public record MatchStartResult(GameMatch match, long hostPlayerId, long guestPlayerId, boolean started) {
}
