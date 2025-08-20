package com.example.questgame.exception;

public class UnauthorizedException extends GameException {
    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
