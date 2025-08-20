package com.example.questgame.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("game_states")
public class GameState {
    @Id
    private Long id;
    private Long userId;
    private String currentLocation;
    private String plotProgress;
    private int health;
}