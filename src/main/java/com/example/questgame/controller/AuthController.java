package com.example.questgame.controller;

import com.example.questgame.model.User;
import com.example.questgame.security.JwtCookieUtil;
import com.example.questgame.security.JwtService;
import com.example.questgame.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Контроллер аутентификации/регистрации.
 * Теперь использует JwtCookieUtil для установки/очистки cookie.
 */
@Controller
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final JwtCookieUtil jwtCookieUtil;

    public AuthController(UserService userService, JwtService jwtService, JwtCookieUtil jwtCookieUtil) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.jwtCookieUtil = jwtCookieUtil;
    }

    @GetMapping("/login")
    public String showLogin(Model model) {
        model.addAttribute("user", new AuthForm());
        return "login";
    }

    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("user", new AuthForm());
        return "register";
    }

    @PostMapping("/register")
    public Mono<String> register(@ModelAttribute("user") AuthForm form,
                                 ServerWebExchange exchange,
                                 Model model) {
        if (isBlank(form.email) || isBlank(form.password)) {
            model.addAttribute("error", "Укажите e-mail и пароль");
            return Mono.just("register");
        }
        return userService.createUser(form.email, form.password)
                .flatMap(u -> issueAuthAndRedirect(exchange, u.getEmail()))
                .onErrorResume(ex -> {
                    log.warn("Регистрация не удалась: {}", ex.getMessage());
                    model.addAttribute("error", "Не удалось создать пользователя: " + ex.getMessage());
                    return Mono.just("register");
                });
    }

    @PostMapping("/login")
    public Mono<String> login(@ModelAttribute("user") AuthForm form,
                              ServerWebExchange exchange,
                              Model model) {
        if (isBlank(form.email) || isBlank(form.password)) {
            model.addAttribute("error", "Укажите e-mail и пароль");
            return Mono.just("login");
        }
        return userService.findByEmail(form.email)
                .switchIfEmpty(Mono.defer(() -> {
                    model.addAttribute("error", "Неверный e-mail или пароль");
                    return Mono.just(new User());
                }))
                .flatMap(u -> {
                    if (u.getId() == null) {
                        return Mono.just("login");
                    }
                    boolean ok = userService.getPasswordEncoder().matches(form.password, u.getPassword());
                    if (!ok) {
                        model.addAttribute("error", "Неверный e-mail или пароль");
                        return Mono.just("login");
                    }
                    return issueAuthAndRedirect(exchange, u.getEmail());
                });
    }

    @PostMapping("/logout")
    public Mono<String> logout(ServerWebExchange exchange) {
        exchange.getResponse().addCookie(jwtCookieUtil.logoutCookie());
        return Mono.just("redirect:/api/auth/login");
    }

    /** Единое место: выставить jwt-куку и уйти на /game. */
    private Mono<String> issueAuthAndRedirect(ServerWebExchange exchange, String subjectEmail) {
        String token = jwtService.generateToken(subjectEmail);
        exchange.getResponse().addCookie(jwtCookieUtil.authCookie(token));
        return Mono.just("redirect:/game");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Простейшая форма для логина/регистрации. */
    public static class AuthForm {
        private String email;
        private String password;

        public AuthForm() {}
        public AuthForm(String email, String password) {
            this.email = email;
            this.password = password;
        }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}