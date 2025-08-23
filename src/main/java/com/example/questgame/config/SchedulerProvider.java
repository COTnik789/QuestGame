package com.example.questgame.config;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Поставщик шедулеров для Reactor, чтобы контролировать планирование подписок из одного места.
 */
@Component
@Primary
public class SchedulerProvider {
    /** CPU-bound задачи (не блокирующие). */
    public Scheduler cpu() { return Schedulers.parallel(); }
    /** Потенциально блокирующие операции. */
    public Scheduler io() { return Schedulers.boundedElastic(); }
}