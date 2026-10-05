package Server.repository;

import Server.model.domain.Account;
import Server.model.domain.Player;

public record AuthenticatedPlayer(Account account, Player player) {
    public boolean isPlayer() {
        return player != null;
    }
}
