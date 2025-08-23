package com.example.questgame.controller;

import com.example.questgame.dto.GameStateDto;
import com.example.questgame.dto.InventoryItemDto;
import com.example.questgame.facade.GameDtoFacade;
import com.example.questgame.model.InventoryItem;
import com.example.questgame.service.GameService;
import com.example.questgame.config.SchedulerProvider;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@Validated
public class InventoryController {

    private final GameService gameService;
    private final GameDtoFacade facade;
    private final SchedulerProvider schedulerProvider;

    public InventoryController(GameService gameService, GameDtoFacade facade, SchedulerProvider schedulerProvider) {
        this.gameService = gameService;
        this.facade = facade;
        this.schedulerProvider = schedulerProvider;
    }

    @GetMapping(value = "/{gameStateId}/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<InventoryItemDto> list(@PathVariable @NotNull Long gameStateId) {
        return gameService.listInventory(gameStateId)
                .map(InventoryController::toDto)
                .subscribeOn(schedulerProvider.cpu());
    }

    @PostMapping("/{gameId}/inventory/use")
    public Mono<GameStateDto> use(
            @PathVariable("gameId") Long gameId,
            @RequestParam("itemId") Long itemId
    ) {
        return gameService.useItem(gameId, itemId)
                .flatMap(facade::from)
                .subscribeOn(schedulerProvider.cpu());
    }

    private static InventoryItemDto toDto(InventoryItem it) {
        return new InventoryItemDto(it.getId(), it.getName(), it.getDescription());
    }
}
