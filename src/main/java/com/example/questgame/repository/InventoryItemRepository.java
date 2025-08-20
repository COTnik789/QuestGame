package com.example.questgame.repository;

import com.example.questgame.model.InventoryItem;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface InventoryItemRepository extends R2dbcRepository<InventoryItem, Long> {

    @Query("""
           SELECT id, game_state_id, name, description
           FROM inventory_items
           WHERE game_state_id = :gameStateId
           """)
    Flux<InventoryItem> findByGameStateId(@Param("gameStateId") Long gameStateId);

    @Query("""
           SELECT id, game_state_id, name, description
           FROM inventory_items
           WHERE game_state_id = :gameStateId
             AND LOWER(name) = LOWER(:name)
           LIMIT 1
           """)
    Mono<InventoryItem> findFirstByGameStateIdAndNameIgnoreCase(@Param("gameStateId") Long gameStateId,
                                                                @Param("name") String name);


}
