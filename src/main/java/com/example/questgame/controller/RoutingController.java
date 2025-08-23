package com.example.questgame.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;

@Controller
public class RoutingController {

    @GetMapping("/login")
    public Mono<String> loginRedirect() {
        return Mono.just("redirect:/api/auth/login");
    }
}
