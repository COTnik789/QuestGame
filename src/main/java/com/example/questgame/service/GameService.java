package com.example.questgame.service;

import com.example.questgame.exception.NotFoundException;
import com.example.questgame.exception.ValidationException;
import com.example.questgame.model.GameState;
import com.example.questgame.model.InventoryItem;
import com.example.questgame.repository.GameStateRepository;
import com.example.questgame.repository.InventoryItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private final GameStateRepository gameStateRepository;
    private final InventoryItemRepository inventoryItemRepository;

    // ---- Локации (технические ключи)
    private static final String LOC_FOREST  = "forest";
    private static final String LOC_CASTLE  = "castle";
    private static final String LOC_CAVE    = "cave";
    private static final String LOC_VILLAGE = "village";

    // ---- Стартовые/игровые константы
    private static final String STARTING_TEXT = "Вы просыпаетесь в древнем лесу. Вокруг густая листва и странные звуки. Выберите путь.";
    private static final int MAX_HEALTH = 100;
    private static final int MIN_HEALTH = 0;

    private static final String RIDDLE_QUESTION = "Что имеет голову, но не имеет тела?";
    @SuppressWarnings("unused")
    private static final List<String> RIDDLE_OPTIONS = List.of("сыр", "лук", "капуста");
    private static final String RIDDLE_CORRECT = "сыр";
    private static final String RIDDLE_PROMPT_MARK = "[RIDDLE]";

    // ---- Рецепты крафта (статичны)
    private static final Map<String, CraftRecipe> RECIPES = Map.of(
            "potion_from_herb", new CraftRecipe(
                    "potion_from_herb", "Сварить зелье",
                    List.of("трава", "фляга"),
                    new Item("зелье", "Зелье лечения (+30 HP)")
            ),
            "light_blade", new CraftRecipe(
                    "light_blade","Клинок света",
                    List.of("меч", "артефакт"),
                    new Item("клинок света","Оружие из артефакта. Поможет против дракона.")
            )
    );

    public GameService(GameStateRepository gameStateRepository, InventoryItemRepository inventoryItemRepository) {
        this.gameStateRepository = gameStateRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    // ==========================
    //           API
    // ==========================

    /** Читаемая метка действия по ключу. */
    public String labelOf(String actionKey) {
        return Action.fromKey(actionKey).map(a -> a.label).orElse(actionKey);
    }

    /** Доступные ключи действий для текущей локации — реактивно. */
    public Flux<String> getAvailableActionKeys(GameState state) {
        String loc = locKey(state.getCurrentLocation());
        return switch (loc) {
            case LOC_FOREST  -> Flux.just(
                    Action.GO_CASTLE.key,
                    Action.SEARCH_TREASURE.key,
                    Action.RUN_AWAY.key,
                    Action.GO_CAVE.key,
                    Action.GO_VILLAGE.key
            );
            case LOC_CAVE    -> Flux.just(
                    Action.SOLVE_RIDDLE.key,
                    Action.GO_VILLAGE.key
            );
            case LOC_VILLAGE -> Flux.just(
                    Action.RETURN_ARTIFACT.key,
                    Action.GO_CAVE.key,
                    Action.RUN_AWAY.key
            );
            case LOC_CASTLE  -> Flux.just(
                    Action.FIGHT_DRAGON.key,
                    Action.SOLVE_RIDDLE.key,
                    Action.RUN_AWAY.key
            );
            default          -> Flux.just(Action.RUN_AWAY.key);
        };
    }

    public boolean isTerminal(GameState gs) {
        if (gs.getHealth() <= 0) return true;
        String msg = Optional.ofNullable(gs.getPlotProgress()).orElse("").toLowerCase(Locale.ROOT);
        return msg.contains("конец!") || msg.contains("игра окончена");
    }

    public boolean riddlePromptActive(GameState gs) {
        String msg = Optional.ofNullable(gs.getPlotProgress()).orElse("");
        return msg.contains(RIDDLE_PROMPT_MARK);
    }

    // ---- State / inventory ----

    public Mono<GameState> createNewGame(Long userId) {
        GameState state = new GameState();
        state.setUserId(userId);
        state.setCurrentLocation("лес");
        state.setPlotProgress(STARTING_TEXT);
        state.setHealth(100);
        return gameStateRepository.save(state);
    }

    /** Сохраняем совместимость по сигнатуре. */
    public Mono<GameState> getUserGames(Long userId) {
        return gameStateRepository.findByUserId(userId);
    }

    public Mono<GameState> byId(Long gameStateId) {
        return findState(gameStateId);
    }

    public Mono<GameState> findState(Long gameStateId) {
        return gameStateRepository.findById(gameStateId)
                .switchIfEmpty(Mono.error(new NotFoundException("GameState", gameStateId)));
    }

    public Flux<InventoryItem> listInventory(Long gameStateId) {
        return inventoryItemRepository.findByGameStateId(gameStateId);
    }

    /** Имена предметов как Flux<String> (lower-case, без пустых). */
    public Flux<String> listInventoryNames(Long gameStateId) {
        return listInventory(gameStateId)
                .map(i -> i.getName() == null ? "" : i.getName())
                .filter(s -> !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT));
    }

    public Mono<Boolean> hasItem(Long gameStateId, String name) {
        return inventoryItemRepository
                .findFirstByGameStateIdAndNameIgnoreCase(gameStateId, name)
                .hasElement();
    }

    public Mono<Void> addItemToInventory(Long gameStateId, String name, String description) {
        InventoryItem item = new InventoryItem();
        item.setGameStateId(gameStateId);
        item.setName(name);
        item.setDescription(description);
        return inventoryItemRepository.save(item).then();
    }

    public Mono<Void> grantItemIfAbsent(Long gameStateId, String name, String description) {
        return hasItem(gameStateId, name)
                .flatMap(exists -> exists ? Mono.<Void>empty()
                        : addItemToInventory(gameStateId, name, description));
    }

    public Mono<Void> removeOneItemByName(Long gameStateId, String name) {
        return inventoryItemRepository.findFirstByGameStateIdAndNameIgnoreCase(gameStateId, name)
                .switchIfEmpty(Mono.error(new NotFoundException("InventoryItem(name)", name)))
                .flatMap(i -> inventoryItemRepository.deleteById(i.getId()));
    }

    private Mono<Void> removeAllInventory(Long gameStateId) {
        return inventoryItemRepository.findByGameStateId(gameStateId)
                .flatMap(i -> inventoryItemRepository.deleteById(i.getId()))
                .then();
    }

    public Mono<GameState> restartGame(Long gameStateId) {
        return findState(gameStateId)
                .flatMap(state -> removeAllInventory(gameStateId)
                        .then(Mono.defer(() -> {
                            state.setHealth(100);
                            state.setCurrentLocation("лес");
                            state.setPlotProgress(STARTING_TEXT);
                            return gameStateRepository.save(state);
                        })));
    }

    // ---- Plot ----

    public Mono<GameState> updatePlot(Long gameStateId, String rawChoice) {
        final String choiceKey = normalizeChoice(rawChoice);

        return findState(gameStateId)
                .switchIfEmpty(Mono.error(new IllegalStateException("GameState not found: " + gameStateId)))
                .flatMap(state -> {
                    if (isTerminal(state)) {
                        log.debug("updatePlot: terminal state id={}", state.getId());
                        return Mono.just(state);
                    }

                    final String loc = locKey(state.getCurrentLocation());

                    return getAvailableActionKeys(state).collectList()
                            .flatMap(keys -> {
                                final Set<String> allowed = new HashSet<>(keys);
                                log.debug("updatePlot: stateId={}, loc={}, choiceRaw={}, choiceKey={}, allowed={}",
                                        state.getId(), state.getCurrentLocation(), rawChoice, choiceKey, allowed);

                                if (!allowed.contains(choiceKey)) {
                                    state.setPlotProgress("Действие недоступно здесь. Выберите один из предложенных вариантов.");
                                    return gameStateRepository.save(state);
                                }

                                return listInventoryNames(state.getId())
                                        .collect(HashSet::new, HashSet::add) // Mono<HashSet<String>>
                                        .flatMap(items -> {
                                            boolean hasSword      = items.contains("меч");
                                            boolean hasArtifact   = items.contains("артефакт");
                                            boolean hasLightBlade = items.contains("клинок света");

                                            Event e = decide(loc, choiceKey, hasSword, hasArtifact, hasLightBlade);

                                            int newHealth = clamp(state.getHealth() + e.deltaHealth(), MIN_HEALTH, MAX_HEALTH);
                                            state.setHealth(newHealth);
                                            state.setPlotProgress(e.message());
                                            if (e.newLocation() != null) {
                                                state.setCurrentLocation(humanLocationName(e.newLocation()));
                                            }
                                            if (newHealth <= 0 && (e.message() == null
                                                    || !e.message().toLowerCase(Locale.ROOT).contains("игра окончена"))) {
                                                state.setPlotProgress(
                                                        (e.message() == null ? "" : e.message()) + " Вы умерли. Игра окончена.");
                                            }

                                            Mono<Void> ops = Mono.empty();

                                            if (e.itemToGrant() != null) {
                                                var it = e.itemToGrant();
                                                String name = it.name() == null ? "" : it.name();
                                                String desc = it.description() == null ? "" : it.description();
                                                if (!name.isBlank()) {
                                                    ops = ops.then(grantItemIfAbsent(state.getId(), name, desc));
                                                }
                                            }
                                            if (e.removeArtifact()) {
                                                ops = ops.then(removeOneItemByName(state.getId(), "артефакт")
                                                        .onErrorResume(__ -> Mono.empty()));
                                            }
                                            if (e.grantSwordIfMissing()) {
                                                ops = ops.then(grantItemIfAbsent(state.getId(), "меч", "Острый меч для боя"));
                                            }

                                            log.debug("updatePlot: apply event stateId={} -> health={}, newLoc={}, msg={}",
                                                    state.getId(), newHealth, state.getCurrentLocation(), state.getPlotProgress());

                                            return ops.then(gameStateRepository.save(state));
                                        })
                                        .doOnError(err -> log.debug("updatePlot ERROR: {}", err.toString(), err));
                            });
                })
                .log("GameService.updatePlot");
    }

    private Event decide(String loc, String choiceKey, boolean hasSword, boolean hasArtifact, boolean hasLightBlade) {
        return switch (loc) {
            case LOC_FOREST -> switch (choiceKey) {
                case "go_castle" -> new Event(
                        "Вы подошли к замку. У входа — дракон. Если у вас есть меч/клинок — сражайтесь, иначе попробуйте решить загадку.",
                        0, null, LOC_CASTLE, false, false);
                case "search_treasure" -> {
                    int roll = ThreadLocalRandom.current().nextInt(10);
                    if (roll >= 7) {
                        yield new Event("Вы нашли меч! Но волк нападает. Здоровье -20.",
                                -20, new Item("меч", "Острый меч для боя"), LOC_FOREST, false, false);
                    } else if (roll >= 5) {
                        yield new Event("Вы нашли траву с сильным ароматом. Похоже, из неё можно сварить зелье.",
                                0, new Item("трава", "Ингредиент для зелья"), LOC_FOREST, false, false);
                    } else if (roll >= 3) {
                        yield new Event("Вы нашли пустую флягу. Пригодится для алхимии.",
                                0, new Item("фляга", "Ингредиент для зелья"), LOC_FOREST, false, false);
                    } else {
                        yield new Event("Вы нашли зелье! Здоровье +30.",
                                +30, new Item("зелье", "Зелье лечения"), LOC_FOREST, false, false);
                    }
                }
                case "run_away" -> new Event("Вы бежите по лесу, но всё ещё тут. Попробуйте другой путь.",
                        0, null, LOC_FOREST, false, false);
                case "go_cave" -> new Event("Вы в тёмной пещере. Здесь можно попытаться решить загадку.",
                        0, null, LOC_CAVE, false, false);
                case "go_village" -> new Event("Вы в деревне. Жители просят найти артефакт в пещере.",
                        0, null, LOC_VILLAGE, false, false);
                default -> new Event("Действие недоступно здесь. Выберите один из предложенных вариантов.",
                        0, null, LOC_FOREST, false, false);
            };
            case LOC_CAVE -> switch (choiceKey) {
                case "solve_riddle" -> new Event(
                        RIDDLE_PROMPT_MARK + " Загадка: " + RIDDLE_QUESTION + " Выберите ответ.",
                        0, null, LOC_CAVE, false, false);
                case "go_village" -> new Event("Вы вернулись в деревню. Жители ждут артефакт.",
                        0, null, LOC_VILLAGE, false, false);
                default -> new Event("Здесь это нельзя. Попробуйте решить загадку или вернуться в деревню.",
                        0, null, LOC_CAVE, false, false);
            };
            case LOC_VILLAGE -> switch (choiceKey) {
                case "return_artifact" -> {
                    if (!hasArtifact) {
                        String reason = hasLightBlade
                                ? "Вы перековали артефакт в Клинок света — жители впечатлены, но артефакта нет."
                                : "У вас нет артефакта. Сначала найдите его в пещере.";
                        yield new Event(reason, 0, null, LOC_VILLAGE, false, false);
                    }
                    yield new Event("Вы вернули артефакт. Жители благодарят и дают вам зелье. Пора к замку.",
                            0, new Item("зелье", "Зелье лечения"), LOC_VILLAGE, true, true);
                }
                case "go_cave" -> new Event("Вы снова в пещере.", 0, null, LOC_CAVE, false, false);
                case "run_away" -> new Event("Вы уходите из деревни и вскоре снова оказываетесь в лесу.",
                        0, null, LOC_FOREST, false, false);
                default -> new Event("Действие недоступно. Доступны: вернуть артефакт (если он у вас), вернуться в пещеру или уйти.",
                        0, null, LOC_VILLAGE, false, false);
            };
            case LOC_CASTLE -> switch (choiceKey) {
                case "fight_dragon" -> {
                    if (hasLightBlade || hasSword) {
                        String end = hasLightBlade
                                ? "Клинок света пронзает чешую дракона. Победа и сокровища. Конец!"
                                : "С мечом вы побеждаете дракона после тяжёлой схватки. Конец!";
                        yield new Event(end, 0, null, LOC_CASTLE, false, false);
                    } else {
                        yield new Event("У вас нет оружия! Дракон ранит вас. Здоровье -50. Попробуйте решить загадку или найти/создать оружие.",
                                -50, null, LOC_CASTLE, false, false);
                    }
                }
                case "solve_riddle" -> new Event("Загадка решена: иногда у дракона больше голов, чем тел. Но вы получили ожог. Здоровье -10.",
                        -10, null, LOC_CASTLE, false, false);
                case "run_away" -> new Event("Вы отступили к лесу, чтобы подготовиться.",
                        0, null, LOC_FOREST, false, false);
                default -> new Event("Действие недоступно у замка. Сражайтесь, решайте загадку или отступайте.",
                        0, null, LOC_CASTLE, false, false);
            };
            default -> new Event("Вы в неизвестном месте. Попробуйте вернуться в лес.",
                    0, null, LOC_FOREST, false, false);
        };
    }

    public Mono<GameState> answerRiddle(Long gameStateId, String rawAnswer) {
        String answer = (rawAnswer == null ? "" : rawAnswer.trim().toLowerCase(Locale.ROOT));

        return findState(gameStateId).flatMap(state -> {
            if (!LOC_CAVE.equals(locKey(state.getCurrentLocation()))) return Mono.just(state);

            if (answer.equals(RIDDLE_CORRECT)) {
                state.setPlotProgress("Верно! Вы нашли артефакт среди камней. Возвращайтесь в деревню за наградой.");
                return grantItemIfAbsent(state.getId(), "артефакт", "Древний артефакт")
                        .then(gameStateRepository.save(state));
            } else {
                state.setHealth(clamp(state.getHealth() - 30, MIN_HEALTH, MAX_HEALTH));
                state.setPlotProgress("Неверно. Монстр из тени атакует. Здоровье -30. Попробуйте снова.");
                return gameStateRepository.save(state);
            }
        });
    }

    /** Доступные рецепты крафта: реактивно и эффективно. */
    public Flux<CraftRecipe> getAvailableCrafts(Long gameStateId) {
        if (gameStateId == null) {
            return Flux.empty();
        }

        return listInventoryNames(gameStateId)
                .collect(HashSet::new, HashSet::add) // Mono<HashSet<String>>
                .flatMapMany(items -> Flux.fromIterable(RECIPES.values())
                        .filter(Objects::nonNull)
                        .filter(r -> {
                            String resultLower = r.result() == null || r.result().name() == null
                                    ? "" : r.result().name().toLowerCase(Locale.ROOT);
                            return !items.contains(resultLower);
                        })
                        .filter(r -> {
                            List<String> req = r.requires();
                            if (req == null) return false;
                            for (String s : req) {
                                if (s == null || !items.contains(s.toLowerCase(Locale.ROOT))) return false;
                            }
                            return true;
                        })
                )
                .onErrorResume(e -> Flux.empty());
    }

    public Mono<GameState> craft(Long gameStateId, String recipeKey) {
        CraftRecipe recipe = RECIPES.get(recipeKey);
        if (recipe == null) return findState(gameStateId);

        return findState(gameStateId).flatMap(state ->
                listInventoryNames(state.getId())
                        .collect(HashSet::new, HashSet::add)
                        .flatMap(itemsLower -> {
                            String resultLower = recipe.result().name() == null ? "" : recipe.result().name().toLowerCase(Locale.ROOT);

                            if (itemsLower.contains(resultLower)) {
                                state.setPlotProgress("У вас уже есть: " + recipe.result().name() + ". Крафт не требуется.");
                                return gameStateRepository.save(state);
                            }

                            boolean ok = recipe.requires().stream()
                                    .map(req -> req == null ? "" : req.toLowerCase(Locale.ROOT))
                                    .allMatch(itemsLower::contains);
                            if (!ok) {
                                state.setPlotProgress("Не хватает компонентов: " + String.join(", ", recipe.requires()) + ".");
                                return gameStateRepository.save(state);
                            }

                            Mono<Void> remove = Mono.empty();
                            for (String req : recipe.requires()) {
                                // удаляем по одному экземпляру, молча игнорируя отсутствие
                                remove = remove.then(removeOneItemByName(state.getId(), req)
                                        .onErrorResume(__ -> Mono.empty()));
                            }
                            Item out = recipe.result();
                            return remove
                                    .then(grantItemIfAbsent(state.getId(), out.name(), out.description()))
                                    .then(Mono.defer(() -> {
                                        state.setPlotProgress("Вы создали: " + out.name() + ". " + state.getPlotProgress());
                                        return gameStateRepository.save(state);
                                    }));
                        })
        );
    }

    public Mono<GameState> useItem(Long gameStateId, Long itemId) {
        Mono<GameState> stateMono = findState(gameStateId);
        Mono<InventoryItem> itemMono = inventoryItemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new NotFoundException("InventoryItem", itemId)));

        return Mono.zip(stateMono, itemMono).flatMap(tuple -> {
            GameState state = tuple.getT1();
            InventoryItem item = tuple.getT2();

            if (!Objects.equals(item.getGameStateId(), gameStateId)) {
                return Mono.error(new ValidationException("Неверный gameStateId для предмета"));
            }

            String name = Optional.ofNullable(item.getName())
                    .map(n -> n.toLowerCase(Locale.ROOT))
                    .orElse("(безымянный)");

            String prevProgress = Optional.ofNullable(state.getPlotProgress()).orElse("");

            switch (name) {
                case "зелье" -> {
                    int newHealth = clamp(
                            state.getHealth() + 30,
                            MIN_HEALTH,
                            MAX_HEALTH
                    );
                    state.setHealth(newHealth);
                    state.setPlotProgress("Вы использовали зелье. Здоровье +30.\n" + prevProgress);
                    return inventoryItemRepository.deleteById(item.getId())
                            .then(gameStateRepository.save(state));
                }
                case "трава", "фляга" -> {
                    state.setPlotProgress("Это компонент. Используйте крафт, чтобы получить зелье.\n" + prevProgress);
                    return gameStateRepository.save(state);
                }
                case "меч", "клинок света", "артефакт" -> {
                    state.setPlotProgress("Этот предмет нельзя использовать напрямую сейчас.\n" + prevProgress);
                    return gameStateRepository.save(state);
                }
                default -> {
                    state.setPlotProgress("Неизвестный предмет: " + name + ".\n" + prevProgress);
                    return gameStateRepository.save(state);
                }
            }
        });
    }

    // ==========================
    //        ВСПОМОГАТЕЛЬНОЕ
    // ==========================

    private String normalizeChoice(String raw) {
        if (raw == null) return "";
        return Action.normalize(raw).map(a -> a.key)
                .orElse(raw.trim().toLowerCase(Locale.ROOT));
    }

    private String humanLocationName(String locKey) {
        return switch (locKey) {
            case LOC_FOREST  -> "лес";
            case LOC_CASTLE  -> "замок";
            case LOC_CAVE    -> "пещера";
            case LOC_VILLAGE -> "деревня";
            default -> "неизвестно";
        };
    }

    private String locKey(String currentLocationHuman) {
        if (currentLocationHuman == null) return LOC_FOREST;
        String s = currentLocationHuman.toLowerCase(Locale.ROOT);
        if (s.contains("лес"))     return LOC_FOREST;
        if (s.contains("замок"))   return LOC_CASTLE;
        if (s.contains("пещера"))  return LOC_CAVE;
        if (s.contains("деревня")) return LOC_VILLAGE;
        return LOC_FOREST;
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private boolean containsGameOver(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("конец!") || m.contains("игра окончена");
    }

    // ==========================
    //     Типы для сюжета/крафта
    // ==========================

    private record Event(String message, int deltaHealth, Item itemToGrant, String newLocation,
                         boolean removeArtifact, boolean grantSwordIfMissing) {}

    public record Item(String name, String description) {}

    public record CraftRecipe(String key, String title, List<String> requires, Item result) {}

    // ==========================
    //     ЕДИНЫЙ ИСТОЧНИК ДЕЙСТВИЙ
    // ==========================

    /** Описание действий: канонический ключ, лейбл для UI, синонимы для нормализации ввода. */
    private enum Action {
        GO_CASTLE      ("go_castle",       "Идти в замок",         "идти в замок", "go to castle"),
        SEARCH_TREASURE("search_treasure", "Искать сокровища",     "искать сокровища", "search for treasure"),
        RUN_AWAY       ("run_away",        "Бежать",                "бежать", "run away"),
        GO_CAVE        ("go_cave",         "Идти в пещеру",         "идти в пещеру", "go to cave"),
        GO_VILLAGE     ("go_village",      "Идти в деревню",        "идти в деревню", "go to village"),
        SOLVE_RIDDLE   ("solve_riddle",    "Решить загадку",        "решить загадку", "solve riddle"),
        FIGHT_DRAGON   ("fight_dragon",    "Сражаться с драконом",  "сражаться с драконом", "fight dragon"),
        RETURN_ARTIFACT("return_artifact", "Вернуться с артефактом","вернуться с артефактом", "return with artifact");

        public final String key;
        public final String label;
        public final List<String> synonyms;

        Action(String key, String label, String... synonyms) {
            this.key = key;
            this.label = label;
            this.synonyms = List.of(synonyms);
        }

        private static final Map<String, Action> BY_KEY =
                Arrays.stream(values()).collect(Collectors.toMap(a -> a.key, a -> a));

        private static final Map<String, Action> NORMALIZER =
                Arrays.stream(values())
                        .flatMap(a -> {
                            Stream<String> s = Stream.concat(Stream.of(a.key), a.synonyms.stream());
                            return s.map(x -> Map.entry(x.toLowerCase(Locale.ROOT), a));
                        })
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b)->a));

        public static Optional<Action> fromKey(String key) {
            return Optional.ofNullable(BY_KEY.get(key));
        }

        public static Optional<Action> normalize(String raw) {
            if (raw == null) return Optional.empty();
            return Optional.ofNullable(NORMALIZER.get(raw.trim().toLowerCase(Locale.ROOT)));
        }
    }
}
