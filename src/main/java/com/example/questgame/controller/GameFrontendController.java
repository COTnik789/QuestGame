package com.example.questgame.controller;

import com.example.questgame.security.JwtService;
import com.example.questgame.service.GameService;
import com.example.questgame.service.UserService;
import org.springframework.http.HttpCookie;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Controller
public class GameFrontendController {

    private final GameService gameService;
    private final UserService userService;
    private final JwtService jwtService;

    public GameFrontendController(GameService gameService,
                                  UserService userService,
                                  JwtService jwtService) {
        this.gameService = gameService;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @GetMapping("/game")
    public Mono<String> gamePage(Authentication authentication,
                                 ServerWebExchange exchange,
                                 Model model) {
        // 1) Достаём email либо из SecurityContext, либо из jwt-куки.
        Mono<String> emailMono;
        if (authentication != null && authentication.getName() != null) {
            emailMono = Mono.just(authentication.getName());
        } else {
            HttpCookie jwt = exchange.getRequest().getCookies().getFirst("jwt");
            if (jwt == null || jwt.getValue().isBlank()) {
                return Mono.just("redirect:/api/auth/login");
            }
            String email = jwtService.parseEmail(jwt.getValue());
            if (email == null || email.isBlank()) {
                return Mono.just("redirect:/api/auth/login");
            }
            emailMono = Mono.just(email);
        }

        // 2) Загружаем/создаём GameState, заполняем модель и отдаём "game".
        return emailMono
                .flatMap(email ->
                        userService.findUserIdByEmail(email)
                                .flatMap(userId ->
                                        gameService.getUserGames(userId)
                                                .next()
                                                .switchIfEmpty(gameService.createNewGame(userId))
                                )
                                .map(gs -> {
                                    model.addAttribute("email", email);
                                    model.addAttribute("gameState", gs);

                                    var actions = gameService.getAvailableActionKeys(gs);
                                    if (actions == null) actions = List.of(); // страховка
                                    model.addAttribute(
                                            "actions",
                                            actions.stream()
                                                    .map(k -> new ActionView(k, gameService.labelOf(k)))
                                                    .toList()
                                    );
                                    return "game";
                                })
                )
                // 3) Если что-то пошло не так (нет пользователя, нет состояния и т.п.) — редиректим на логин.
                .onErrorResume(e -> Mono.just("redirect:/api/auth/login"));
    }

    public record ActionView(String key, String label) {}
}
