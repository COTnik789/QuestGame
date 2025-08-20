package com.example.questgame.security;

import com.example.questgame.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Хелпер для построения cookie с JWT.
 */
@Component
public class JwtCookieUtil {

    private final JwtProperties props;

    public JwtCookieUtil(JwtProperties props) {
        this.props = props;
    }

    /** Кука с токеном на срок действия JWT. */
    public ResponseCookie authCookie(String token) {
        return ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(false)         // включи true при HTTPS
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(Math.max(0, props.getExpirationSeconds())))
                .build();
    }

    /** Кука для логаута (гасит токен). */
    public ResponseCookie logoutCookie() {
        return ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}