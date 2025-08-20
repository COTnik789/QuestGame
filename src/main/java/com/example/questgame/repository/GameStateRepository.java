package com.example.questgame.repository;

import com.example.questgame.model.GameState;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

public interface GameStateRepository extends R2dbcRepository<GameState, Long> {
    Flux<GameState> findByUserId(Long userId);
}
