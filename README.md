# Quest Game (Spring WebFlux + R2DBC + JWT)

![Build](https://github.com/COTnik789/QuestGame/actions/workflows/maven.yml/badge.svg?branch=master)

Небольшая текстовая квест‑игра для портфолио. Стек: **Java 17**, **Spring WebFlux**, **R2DBC (MySQL)**, **JWT**, **Thymeleaf**.

## Запуск локально

1. Создать БД MySQL:
   ```sql
   CREATE DATABASE questgame CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```

2. Настроить доступ в `src/main/resources/application.properties`:
   ```properties
   spring.r2dbc.url=r2dbc:mysql://127.0.0.1:3306/questgame?serverTimezone=UTC
   spring.r2dbc.username=root
   spring.r2dbc.password=root
   ```

3. Запустить приложение:
   ```bash
   ./mvnw spring-boot:run
   ```

4. Открыть в браузере:
   - Форма логина: `http://localhost:8080/api/auth/login`
   - Форма регистрации: `http://localhost:8080/api/auth/register`
   - Игра: `http://localhost:8080/game` (после логина/регистрации)

## Тестовые данные

Скрипты `schema.sql` / `data.sql` содержат схему и демо‑данные (используются в тестах).

## Профили

- `application.properties` — дефолт для локалки/prod.
- `application-test.yml` — настройки для тестов.

## Архитектура

- **controller**: `AuthController`, `GameController`, `InventoryController`, `GameFrontendController`, `RoutingController`
- **service**: `UserService`, `com.example.questgame.service.`
- **repository**: R2DBC репозитории для `User`, `GameState`, `InventoryItem`
- **security**: `SecurityConfig`, `JwtWebFilter`, `JwtService`, `JwtCookieUtil`
- **dto/facade**: `*Dto`, `GameDtoFacade`
- **exception**: `*Exception`, `GlobalExceptionHandler`, `ApiError`
- **view**: `Thymeleaf` шаблоны (`login.html`, `register.html`, `game.html`) + `style.css`

## Полезные команды

```bash
# сборка и тесты
./mvnw clean verify

# запуск локально
./mvnw spring-boot:run
```