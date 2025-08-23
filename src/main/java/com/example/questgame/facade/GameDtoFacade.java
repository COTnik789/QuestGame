package com.example.questgame.facade;

import com.example.questgame.dto.*;
import com.example.questgame.model.GameState;
import com.example.questgame.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Component
public class GameDtoFacade {

    private final GameService gameService;

    public GameDtoFacade(GameService gameService) {
        this.gameService = gameService;
    }

    /** Построить GameStateDto из сущности, подтянув действия/крафты/загадку. */
    public Mono<GameStateDto> from(GameState gs) {
        if (gs == null || gs.getId() == null) {
            return Mono.error(new IllegalArgumentException("GameState is null or has no ID"));
        }

        final boolean terminal = gameService.isTerminal(gs);

        final String progress = gs.getPlotProgress() == null ? "" : gs.getPlotProgress();
        final String location = gs.getCurrentLocation() == null ? "Неизвестно" : gs.getCurrentLocation();

        final Mono<List<ActionDto>> actionsMono =
                terminal ? Mono.just(List.of())
                        : gameService.getAvailableActionKeys(gs)
                        .map(k -> new ActionDto(k, gameService.labelOf(k)))
                        .collectList();

        final RiddleDto riddle =
                (!terminal && gameService.riddlePromptActive(gs))
                        ? new RiddleDto(
                        "Загадка: Что имеет голову, но не имеет тела?",
                        List.of("сыр", "лук", "капуста")
                )
                        : null;

        return actionsMono.flatMap(actionsVal ->
                gameService.getAvailableCrafts(gs.getId())
                        .collectList()
                        .onErrorReturn(List.of())
                        .defaultIfEmpty(List.of())
                        .map(recipes -> new GameStateDto(
                                gs.getId(),
                                progress,
                                gs.getHealth(),
                                location,
                                actionsVal,
                                terminal,
                                riddle,
                                recipes.stream()
                                        .filter(Objects::nonNull)
                                        .map(r -> {
                                            var result = r.result();
                                            return new CraftDto(
                                                    r.key(),
                                                    r.title(),
                                                    r.requires(),
                                                    result == null
                                                            ? new ItemDto("", "")
                                                            : new ItemDto(
                                                            result.name(),
                                                            result.description()
                                                    )
                                            );
                                        })
                                        .toList()
                        ))
        );
    }

    /** Удобный хелпер для случаев, когда у нас Mono<GameState>. */
    public Mono<GameStateDto> from(Mono<GameState> gsMono) {
        return gsMono
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found")))
                .flatMap(this::from);
    }

    /** Построить DTO по id состояния. */
    public Mono<GameStateDto> byId(Long gameStateId) {
        return gameService.byId(gameStateId).flatMap(this::from);
    }
}