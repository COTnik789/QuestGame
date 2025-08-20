package com.example.questgame.service;

import com.example.questgame.model.User;
import com.example.questgame.repository.UserRepository;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final UserRepository userRepository;
    @Getter
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Mono<User> createUser(String email, String password) {
        logger.debug("Попытка создать пользователя с email: {}", email);
        return userRepository.findByEmail(email)
                .hasElement()
                .flatMap(exists -> {
                    if (exists) {
                        logger.warn("Пользователь с email {} уже существует", email);
                        return Mono.error(new RuntimeException("Пользователь с таким email уже существует"));
                    }
                    logger.debug("Создание нового пользователя с email: {}", email);
                    User user = new User();
                    user.setEmail(email);
                    user.setPassword(passwordEncoder.encode(password));
                    return userRepository.save(user)
                            .doOnSuccess(saved -> logger.debug("Пользователь сохранён: {}", saved));
                })
                .doOnError(error -> logger.error("Ошибка при создании пользователя: {}", error.getMessage()));
    }

    public Mono<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Mono<Long> findUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(User::getId)
                .switchIfEmpty(Mono.error(new RuntimeException("Пользователь не найден")));
    }

}