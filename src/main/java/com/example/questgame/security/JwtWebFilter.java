package com.example.questgame.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Лёгкий JWT-фильтр: если есть валидный токен — кладём Authentication в контекст.
 * Ничего не "роняет" сам по себе: отсутствие токена на приватных маршрутах
 * обработает SecurityConfig (вернёт 401/403).
 */
@Component
@ConditionalOnProperty(name = "app.security.jwt.enabled", havingValue = "true", matchIfMissing = true)
public class JwtWebFilter implements WebFilter {

    private final JwtService jwtService;

    public JwtWebFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
            "/api/auth", "/login", "/register",
            "/css", "/js", "/images", "/webjars", "/favicon.ico", "/static",
            "/", "/game" // /game разрешён в SecurityConfig; токен здесь не обязателен
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // пропускаем CORS preflight
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().value();

        // попытаться распарсить токен всегда; если невалиден — просто идём дальше
        String token = resolveToken(exchange);
        if (token != null && !token.isBlank()) {
            String email = jwtService.parseEmail(token);
            if (email != null && !email.isBlank()) {
                Authentication auth = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                );
                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
            }
        }

        // нет токена или он невалиден — для публичных путей просто идём дальше
        if (isPublic(path)) {
            return chain.filter(exchange);
        }

        // для приватных путей пусть решает SecurityConfig (401/403)
        return chain.filter(exchange);
    }

    private boolean isPublic(String path) {
        if (path == null || path.isEmpty()) return true;
        for (String p : PUBLIC_PREFIXES) {
            if (path.equals(p) || path.startsWith(p + "/")) return true;
        }
        return false;
    }

    private String resolveToken(ServerWebExchange exchange) {
        // Authorization: Bearer xxx
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // cookie: jwt=xxx
        HttpCookie cookie = exchange.getRequest().getCookies().getFirst("jwt");
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            return cookie.getValue();
        }
        return null;
    }
}