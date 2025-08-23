package com.example.questgame.controller;

import com.example.questgame.QuestGameApplication;
import com.example.questgame.config.TestSecurityConfig;
import com.example.questgame.model.GameState;
import com.example.questgame.security.JwtWebFilter;
import com.example.questgame.service.GameService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = QuestGameApplication.class
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class InventoryControllerWebTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private GameService gameService;

    @MockBean
    private JwtWebFilter jwtWebFilter;

    @BeforeEach
    void bypassSecurityFilter() {
        Mockito.when(jwtWebFilter.filter(Mockito.any(ServerWebExchange.class), Mockito.any(WebFilterChain.class)))
                .thenAnswer(inv -> {
                    ServerWebExchange exchange = inv.getArgument(0);
                    WebFilterChain chain = inv.getArgument(1);
                    return chain.filter(exchange);
                });

        Mockito.when(gameService.getAvailableCrafts(Mockito.anyLong()))
                .thenReturn(Flux.empty());

        Mockito.when(gameService.getAvailableActionKeys(Mockito.any(GameState.class)))
                .thenReturn(Flux.empty());
    }

    @Test
    @DisplayName("POST /api/games/{id}/inventory/use — OK, возвращает обновлённый GameStateDto")
    void use_returnsUpdatedState() {
        long gameStateId = 9L;
        long itemId = 7L;

        GameState state = new GameState();
        state.setId(gameStateId);
        state.setCurrentLocation("Пещера");
        state.setPlotProgress("Вы использовали предмет");
        state.setHealth(85);

        Mockito.when(gameService.useItem(gameStateId, itemId))
                .thenReturn(Mono.just(state));

        webTestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/games/{gameStateId}/inventory/use")
                        .queryParam("itemId", itemId)
                        .build(gameStateId))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo((int) gameStateId)
                .jsonPath("$.currentLocation").isEqualTo("Пещера")
                .jsonPath("$.health").isEqualTo(85);
    }
}
