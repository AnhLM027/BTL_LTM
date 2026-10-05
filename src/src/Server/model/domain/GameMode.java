package Server.model.domain;

import Server.model.enums.GameModeCode;

public record GameMode(
        int modeId,
        GameModeCode modeCode,
        String modeName,
        String description,
        boolean active
) {
}
