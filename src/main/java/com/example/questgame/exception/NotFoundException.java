package com.example.questgame.exception;

import java.util.Map;

public class NotFoundException extends GameException {
    public NotFoundException(String what, Object id) {
        super(ErrorCode.NOT_FOUND, what + " not found",
                Map.of("entity", what, "id", id));
    }
}
