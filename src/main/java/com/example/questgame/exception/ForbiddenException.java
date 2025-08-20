package com.example.questgame.exception;

public class ForbiddenException extends GameException {
    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, message);
    }
}
