package com.example.questgame.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Table("inventory_items")
public class InventoryItem {
    @Id
    private Long id;

    @Column("game_state_id")
    private Long gameStateId;

    private String name;
    private String description;
}
