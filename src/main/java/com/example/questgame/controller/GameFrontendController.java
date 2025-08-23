package com.example.questgame.controller;

import com.example.questgame.model.GameState;
import com.example.questgame.security.JwtService;
import com.example.questgame.service.GameService;
import com.example.questgame.service.UserService;
import org.springframework.http.HttpCookie;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import com.example.questgame.config.SchedulerProvider;

import java.util.List;

/**
 * Отдаёт страницу игры и наполняет модель данными.
 * Разбито на компактные методы, без if/else на верхнем уровне — используется реактивный стиль.
 */
@Controller
public class GameFrontendController {

    private final JwtService jwtService;
    private final GameService gameService;
    private final SchedulerProvider schedulerProvider;
    private final UserService userService;

    public GameFrontendController(JwtService jwtService, GameService gameService, UserService userService, SchedulerProvider schedulerProvider) {
        this.jwtService = jwtService;
        this.gameService = gameService;
        this.userService = userService;
        this.schedulerProvider = schedulerProvider;
    }

    @GetMapping("/game")
    public Mono<String> gamePage(Authentication authentication,
                                 ServerWebExchange exchange,
                                 Model model) {

        return resolveEmail(authentication, exchange)
                .flatMap(email -> loadOrCreateState(email)
                        .flatMap(state -> gameService.getAvailableActionKeys(state)
                                .map(k -> new ActionView(k, gameService.labelOf(k)))
                                .collectList()
                                .map(actions -> prepareGameView(model, email, state, actions))
                        ).subscribeOn(schedulerProvider.cpu())
                .switchIfEmpty(Mono.just("redirect:/api/auth/login"))
                .onErrorResume(e -> Mono.just("redirect:/api/auth/login")));
    }

    /** Достаём e-mail из Authentication или из JWT-куки. Пустой Mono, если ничего нет. */
    private Mono<String> resolveEmail(Authentication authentication, ServerWebExchange exchange) {
        return Mono.justOrEmpty(authentication)
                .map(Authentication::getName)
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.defer(() -> Mono.justOrEmpty(exchange.getRequest().getCookies().getFirst("jwt"))
                        .map(HttpCookie::getValue)
                        .filter(StringUtils::hasText)
                        .map(jwtService::parseEmail)
                        .filter(StringUtils::hasText)
                ));
    }

    /** Загружаем состояние пользователя или создаём новое. */
    private Mono<GameState> loadOrCreateState(String email) {
        return userService.findUserIdByEmail(email)
                .flatMap(userId -> gameService.getUserGames(userId)
                        .switchIfEmpty(gameService.createNewGame(userId)));
    }

    /** Наполняем модель и возвращаем имя шаблона. */
    private String prepareGameView(Model model, String email, GameState state, java.util.List<ActionView> actions) {
        model.addAttribute("email", email);
        model.addAttribute("gameState", state);
        model.addAttribute("actions", actions);
        return "game";
    }

    public record ActionView(String key, String label) {}
}