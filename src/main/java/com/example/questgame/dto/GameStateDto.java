package com.example.questgame.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameStateDto {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("plotProgress")
    private String plotProgress;

    @JsonProperty("health")
    private Integer health;

    @JsonProperty("currentLocation")
    private String currentLocation;

    @JsonProperty("actions")
    private List<ActionDto> actions;

    @JsonProperty("terminal")
    private boolean terminal;

    @JsonProperty("riddle")
    private RiddleDto riddle;

    @JsonProperty("crafts")
    private List<CraftDto> crafts;
}
