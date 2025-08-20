package com.example.questgame.exception;

public class BusinessRuleException extends GameException {
    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
