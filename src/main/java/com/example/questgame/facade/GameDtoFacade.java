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
import java.util.Optional;

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

        final List<ActionDto> actions =
                terminal ? List.of()
                        : gameService.getAvailableActionKeys(gs) != null
                        ? gameService.getAvailableActionKeys(gs).stream()
                        .map(k -> new ActionDto(k, gameService.labelOf(k)))
                        .toList()
                        : List.of();

        final RiddleDto riddle =
                (!terminal && gameService.riddlePromptActive(gs))
                        ? new RiddleDto(
                        "Загадка: Что имеет голову, но не имеет тела?",
                        List.of("сыр", "лук", "капуста")
                )
                        : null;

        return gameService.getAvailableCrafts(gs.getId())
                .onErrorReturn(List.of())     // если сервис упадёт → пустой список
                .defaultIfEmpty(List.of())    // если Mono вернёт пусто
                .map(recipes -> new GameStateDto(
                        gs.getId(),
                        progress,
                        gs.getHealth(),
                        location,
                        actions,
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
                ));
    }







    /** Удобный хелпер для случаев, когда у нас Mono<GameState>. */
    public Mono<GameStateDto> from(Mono<GameState> gsMono) {
        return gsMono
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Game state not found")))
                .flatMap(this::from);
    }


    /** Построить DTO по id состояния. */
    public Mono<GameStateDto> byId(Long gameStateId) {
        return gameService.findState(gameStateId).flatMap(this::from);
    }
}
