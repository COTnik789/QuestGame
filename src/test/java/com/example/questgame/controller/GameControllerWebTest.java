package com.example.questgame.controller;

import com.example.questgame.QuestGameApplication;
import com.example.questgame.config.TestSecurityConfig;
import com.example.questgame.model.GameState;
import com.example.questgame.security.JwtWebFilter;
import com.example.questgame.service.GameService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

import java.util.List;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = QuestGameApplication.class
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class GameControllerWebTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GameService gameService;

    @MockBean
    private JwtWebFilter jwtWebFilter;

    @BeforeAll
    static void enableReactorDebug() {
        Hooks.onOperatorDebug();
    }

    @BeforeEach
    void bypassSecurityFilter() {
        Mockito.when(jwtWebFilter.filter(Mockito.any(ServerWebExchange.class), Mockito.any(WebFilterChain.class)))
                .thenAnswer(inv -> {
                    ServerWebExchange exchange = inv.getArgument(0);
                    WebFilterChain chain = inv.getArgument(1);
                    return chain.filter(exchange);
                });

        // 🔑 ключевой стаб
        Mockito.when(gameService.getAvailableCrafts(Mockito.anyLong()))
                .thenReturn(Mono.just(List.of()));
    }

    @Test
    @DisplayName("POST /api/games/progress — OK, возвращает GameState/DTO")
    void progress_ok_returnsGameState() {
        long gameStateId = 5L;
        String choice = "Идти в лес";

        GameState state = new GameState();
        state.setId(gameStateId);
        state.setCurrentLocation("Лес");
        state.setPlotProgress("Начало");
        state.setHealth(100);

        Mockito.when(gameService.updatePlot(gameStateId, choice))
                .thenReturn(Mono.just(state));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/games/progress")
                        .queryParam("gameStateId", gameStateId)
                        .queryParam("choice", choice)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo((int) gameStateId)
                .jsonPath("$.currentLocation").isEqualTo("Лес")
                .jsonPath("$.health").isEqualTo(100);
    }

    @Test
    @DisplayName("POST /api/games/progress — NOT_FOUND, если игра не найдена")
    void progress_notFound_returns404() {
        long gameStateId = 42L;
        String choice = "Куда-то";

        Mockito.when(gameService.updatePlot(gameStateId, choice))
                .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found")));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/games/progress")
                        .queryParam("gameStateId", gameStateId)
                        .queryParam("choice", choice)
                        .build())
                .exchange()
                .expectStatus().isNotFound();
    }
}
