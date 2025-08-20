package com.example.questgame.service;

import com.example.questgame.model.User;
import com.example.questgame.security.JwtService;
import com.example.questgame.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

/**
 * Смок-тест авторизационного потока.
 * Принимаем любой 3xx редирект (303/302), главное — Location: /game.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
public class AuthFlowWebTest {

    @Autowired
    WebTestClient webTestClient;

    @MockBean
    UserService userService;

    @MockBean
    JwtService jwtService;

    @Test
    void registerRedirectsToGame() {
        Mockito.when(userService.createUser(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(Mono.just(user(10L, "u1@example.com", "{noop}pwd")));
        Mockito.when(jwtService.generateToken(Mockito.anyString()))
                .thenReturn("dummy.jwt");

        webTestClient.post()
                .uri("/api/auth/register")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("email", "u1@example.com")
                        .with("password", "pwd"))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", "/game");
    }

    @Test
    void loginRedirectsToGame() {
        PasswordEncoder enc = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        User u = user(20L, "u2@example.com", enc.encode("pwd"));

        Mockito.when(userService.findByEmail(Mockito.anyString()))
                .thenReturn(Mono.just(u));
        Mockito.when(userService.getPasswordEncoder())
                .thenReturn(enc);
        Mockito.when(jwtService.generateToken(Mockito.anyString()))
                .thenReturn("dummy.jwt");

        webTestClient.post()
                .uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("email", "u2@example.com")
                        .with("password", "pwd"))
                .exchange()
                .expectStatus().is3xxRedirection()
                .expectHeader().valueEquals("Location", "/game");
    }

    private static User user(Long id, String email, String password) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPassword(password);
        return u;
    }
}