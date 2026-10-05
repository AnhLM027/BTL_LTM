package Server.model.domain;

import Server.model.enums.AccountRole;
import Server.model.enums.AccountStatus;

import java.time.LocalDateTime;

public record Account(
        long accountId,
        String username,
        String passwordHash,
        AccountRole role,
        AccountStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
