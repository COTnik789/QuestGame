package com.example.questgame.dto;

import java.util.List;

public record GameStateDto(
        Long id,
        String plotProgress,
        Integer health,
        String currentLocation,
        List<ActionDto> actions,
        boolean terminal,
        RiddleDto riddle,
        List<CraftDto> crafts
) {}
