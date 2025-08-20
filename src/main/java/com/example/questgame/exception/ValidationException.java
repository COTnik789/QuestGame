package com.example.questgame.exception;

public class ValidationException extends GameException {
    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_FAILED, message);
    }
}
