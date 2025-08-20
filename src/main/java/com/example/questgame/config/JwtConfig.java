package com.example.questgame.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Активирует биндинг JwtProperties. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
}