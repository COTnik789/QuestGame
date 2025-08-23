package com.example.questgame.controller;

import com.example.questgame.dto.CraftDto;
import com.example.questgame.dto.GameStateDto;
import com.example.questgame.facade.GameDtoFacade;
import com.example.questgame.service.GameService;
import com.example.questgame.config.SchedulerProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/games")
@Validated
public class GameController {

    private final GameService gameService;
    private final GameDtoFacade facade;
    private final SchedulerProvider schedulerProvider;

    public GameController(GameService gameService, GameDtoFacade facade, SchedulerProvider schedulerProvider) {
        this.gameService = gameService;
        this.facade = facade;
        this.schedulerProvider = schedulerProvider;
    }

    /** Сделать шаг сюжета. */
    @PostMapping("/progress")
    public Mono<GameStateDto> updatePlot(
            @RequestParam("gameStateId") Long gameStateId,
            @RequestParam("choice") String choice
    ) {
        return gameService.updatePlot(gameStateId, choice)
                .flatMap(facade::from)
                .subscribeOn(schedulerProvider.cpu());
    }

    /** Ответить на загадку. */
    @PostMapping(value = "/riddle/answer", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GameStateDto> answerRiddle(
            @RequestParam @NotNull Long gameStateId,
            @RequestParam @NotBlank String answer
    ) {
        return gameService.answerRiddle(gameStateId, answer)
                .flatMap(facade::from)
                .subscribeOn(schedulerProvider.cpu());
    }

    /** Перезапустить игру. */
    @PostMapping(value = "/restart", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GameStateDto> restart(@RequestParam @NotNull Long gameStateId) {
        return gameService.restartGame(gameStateId)
                .flatMap(facade::from)
                .subscribeOn(schedulerProvider.cpu());
    }

    /** Доступные рецепты крафта. */
    @GetMapping(value = "/{gameStateId}/craft/available", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<CraftDto> availableCrafts(@PathVariable @NotNull Long gameStateId) {
        return gameService.getAvailableCrafts(gameStateId)
                .map(r -> new CraftDto(
                        r.key(),
                        r.title(),
                        r.requires(),
                        new com.example.questgame.dto.ItemDto(
                                r.result() == null ? "" : r.result().name(),
                                r.result() == null ? "" : r.result().description()
                        )
                ));
    }

    /** Выполнить крафт. */
    @PostMapping("/{gameStateId}/craft")
    public Mono<GameStateDto> craft(
            @PathVariable Long gameStateId,
            @RequestParam("recipeKey") String recipeKey
    ) {
        return gameService.craft(gameStateId, recipeKey)
                .flatMap(facade::from)
                .subscribeOn(schedulerProvider.cpu());
    }

    /** Получить текущее состояние (полезно фронту для синхронизации). */
    @GetMapping(value = "/{gameStateId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GameStateDto> get(@PathVariable @NotNull Long gameStateId) {
        return facade.byId(gameStateId)
                .subscribeOn(schedulerProvider.cpu());
    }
}
