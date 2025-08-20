package com.example.questgame.dto;

import java.util.List;

public record CraftDto(String key, String title, List<String> requires, ItemDto result) {}
