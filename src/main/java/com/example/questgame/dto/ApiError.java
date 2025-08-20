package com.example.questgame.dto;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        Instant timestamp,
        String path,
        int status,
        String error,
        String code,
        String message,
        Map<String, Object> details
) {
    public static ApiError of(String path, int status, String error, String code, String message, Map<String, Object> details) {
        return new ApiError(Instant.now(), path, status, error, code, message, details);
    }
}
