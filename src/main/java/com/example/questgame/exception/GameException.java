package com.example.questgame.exception;

import java.util.Map;

public class GameException extends RuntimeException {
    private final ErrorCode code;
    private final Map<String, Object> details;

    public GameException(ErrorCode code, String message) {
        super(message);
        this.code = code;
        this.details = Map.of();
    }

    public GameException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getCode() { return code; }
    public Map<String, Object> getDetails() { return details; }
}
