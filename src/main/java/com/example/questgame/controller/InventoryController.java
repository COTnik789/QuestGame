package com.example.questgame.controller;

import com.example.questgame.dto.GameStateDto;
import com.example.questgame.dto.InventoryItemDto;
import com.example.questgame.facade.GameDtoFacade;
import com.example.questgame.model.InventoryItem;
import com.example.questgame.service.GameService;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/games")
@Validated
public class InventoryController {

    private final GameService gameService;
    private final GameDtoFacade facade;

    public InventoryController(GameService gameService, GameDtoFacade facade) {
        this.gameService = gameService;
        this.facade = facade;
    }

    @GetMapping(value = "/{gameStateId}/inventory", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<List<InventoryItemDto>>> list(@PathVariable @NotNull Long gameStateId) {
        return gameService.listInventory(gameStateId)
                .map(InventoryController::toDto)
                .collectList()
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{gameId}/inventory/use")
    public Mono<GameStateDto> use(
            @PathVariable("gameId") Long gameId,
            @RequestParam("itemId") Long itemId
    ) {
        return gameService.useItem(gameId, itemId)
                .flatMap(facade::from);
    }

    private static InventoryItemDto toDto(InventoryItem it) {
        return new InventoryItemDto(it.getId(), it.getName(), it.getDescription());
    }
}
