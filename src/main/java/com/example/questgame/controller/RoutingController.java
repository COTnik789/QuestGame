package com.example.questgame.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoutingController {

    @GetMapping("/login")
    public String loginRedirect() {
        return "redirect:/api/auth/login";
    }
}
