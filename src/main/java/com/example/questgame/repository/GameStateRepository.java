package com.example.questgame.repository;

import com.example.questgame.model.GameState;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Mono;

//Заменить Flux на Mono - ЗАМЕНИЛ
public interface GameStateRepository extends R2dbcRepository<GameState, Long> {
    Mono<GameState> findByUserId(Long userId);
}
