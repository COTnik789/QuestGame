package com.example.questgame.service;

import com.example.questgame.exception.NotFoundException;
import com.example.questgame.exception.ValidationException;
import com.example.questgame.model.GameState;
import com.example.questgame.model.InventoryItem;
import com.example.questgame.repository.GameStateRepository;
import com.example.questgame.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

@Service
public class GameService {
    private final GameStateRepository gameStateRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final Random random = new Random();

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("go_castle", "Идти в замок"),
            Map.entry("search_treasure", "Искать сокровища"),
            Map.entry("run_away", "Бежать"),
            Map.entry("go_cave", "Идти в пещеру"),
            Map.entry("go_village", "Идти в деревню"),
            Map.entry("solve_riddle", "Решить загадку"),
            Map.entry("fight_dragon", "Сражаться с драконом"),
            Map.entry("return_artifact", "Вернуться с артефактом")
    );

    private static final Map<String, String> CHOICE_MAP = Map.ofEntries(
            Map.entry("go_castle", "go_castle"),
            Map.entry("search_treasure", "search_treasure"),
            Map.entry("run_away", "run_away"),
            Map.entry("go_cave", "go_cave"),
            Map.entry("go_village", "go_village"),
            Map.entry("solve_riddle", "solve_riddle"),
            Map.entry("fight_dragon", "fight_dragon"),
            Map.entry("return_artifact", "return_artifact"),
            Map.entry("идти в замок", "go_castle"),
            Map.entry("искать сокровища", "search_treasure"),
            Map.entry("бежать", "run_away"),
            Map.entry("идти в пещеру", "go_cave"),
            Map.entry("идти в деревню", "go_village"),
            Map.entry("решить загадку", "solve_riddle"),
            Map.entry("сражаться с драконом", "fight_dragon"),
            Map.entry("вернуться с артефактом", "return_artifact"),
            Map.entry("go to castle", "go_castle"),
            Map.entry("search for treasure", "search_treasure"),
            Map.entry("run away", "run_away"),
            Map.entry("go to cave", "go_cave"),
            Map.entry("go to village", "go_village"),
            Map.entry("solve riddle", "solve_riddle"),
            Map.entry("fight dragon", "fight_dragon"),
            Map.entry("return with artifact", "return_artifact")
    );

    private static final String LOC_FOREST  = "forest";
    private static final String LOC_CASTLE  = "castle";
    private static final String LOC_CAVE    = "cave";
    private static final String LOC_VILLAGE = "village";

    private static final String STARTING_TEXT = "Вы просыпаетесь в древнем лесу. Вокруг густая листва и странные звуки. Выберите путь.";
    private static final int MAX_HEALTH = 100;
    private static final int MIN_HEALTH = 0;

    private static final String RIDDLE_QUESTION = "Что имеет голову, но не имеет тела?";
    private static final List<String> RIDDLE_OPTIONS = List.of("сыр", "лук", "капуста");
    private static final String RIDDLE_CORRECT = "сыр";
    private static final String RIDDLE_PROMPT_MARK = "[RIDDLE]";

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

    public String labelOf(String actionKey) { return ACTION_LABELS.getOrDefault(actionKey, actionKey); }

    /** Теперь реактивно: поток доступных ключей действий. */
    public Flux<String> getAvailableActionKeys(GameState state) {
        String loc = locKey(state.getCurrentLocation());
        return switch (loc) {
            case LOC_FOREST  -> Flux.just("go_castle", "search_treasure", "run_away", "go_cave", "go_village");
            case LOC_CAVE    -> Flux.just("solve_riddle", "go_village");
            case LOC_VILLAGE -> Flux.just("return_artifact", "go_cave", "run_away");
            case LOC_CASTLE  -> Flux.just("fight_dragon", "solve_riddle", "run_away");
            default          -> Flux.just("run_away");
        };
    }

    public boolean isTerminal(GameState gs) {
        if (gs.getHealth() <= 0) return true;
        String msg = (gs.getPlotProgress() == null ? "" : gs.getPlotProgress()).toLowerCase(Locale.ROOT);
        return msg.contains("конец!") || msg.contains("игра окончена");
    }

    public boolean riddlePromptActive(GameState gs) {
        String msg = gs.getPlotProgress() == null ? "" : gs.getPlotProgress();
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

    public Mono<GameState> getUserGames(Long userId) { return gameStateRepository.findByUserId(userId); }

    public Mono<GameState> byId(Long gameStateId) {
        return findState(gameStateId);
    }

    public Mono<GameState> findState(Long gameStateId) {
        return gameStateRepository.findById(gameStateId)
                .switchIfEmpty(Mono.error(new NotFoundException("GameState", gameStateId)));
    }

    public Flux<InventoryItem> listInventory(Long gameStateId) { return inventoryItemRepository.findByGameStateId(gameStateId); }

    /** Теперь реактивно: имена предметов как Flux<String>, уже в lower-case и без пустых. */
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
                    if (Boolean.TRUE.equals(isTerminal(state))) {
                        System.out.println("DEBUG updatePlot: terminal state id=" + state.getId());
                        return Mono.just(state);
                    }

                    final String loc = locKey(state.getCurrentLocation());

                    return getAvailableActionKeys(state).collectList()
                            .flatMap(keys -> {
                                final Set<String> allowed = new java.util.HashSet<>(keys);
                                System.out.println("DEBUG updatePlot: stateId=" + state.getId()
                                        + ", loc=" + state.getCurrentLocation()
                                        + ", choiceRaw=" + rawChoice
                                        + ", choiceKey=" + choiceKey
                                        + ", allowed=" + allowed);

                                if (!allowed.contains(choiceKey)) {
                                    state.setPlotProgress("Действие недоступно здесь. Выберите один из предложенных вариантов.");
                                    return gameStateRepository.save(state);
                                }

                                return listInventoryNames(state.getId())
                                        .collectList()
                                        .defaultIfEmpty(List.of())
                                        .flatMap(items -> {
                                            boolean hasSword = items.contains("меч");
                                            boolean hasArtifact = items.contains("артефакт");
                                            boolean hasLightBlade = items.contains("клинок света");

                                            Event e = decide(loc, choiceKey, hasSword, hasArtifact, hasLightBlade);

                                            int newHealth = clamp((state.getHealth()) + e.deltaHealth(), MIN_HEALTH, MAX_HEALTH);
                                            state.setHealth(newHealth);
                                            state.setPlotProgress(e.message());
                                            if (e.newLocation() != null) {
                                                state.setCurrentLocation(humanLocationName(e.newLocation()));
                                            }
                                            if (newHealth <= 0 && (e.message() == null || !e.message().toLowerCase(Locale.ROOT).contains("игра окончена"))) {
                                                state.setPlotProgress((e.message() == null ? "" : e.message()) + " Вы умерли. Игра окончена.");
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
                                                ops = ops.then(removeOneItemByName(state.getId(), "артефакт").onErrorResume(__ -> Mono.empty()));
                                            }
                                            if (e.grantSwordIfMissing()) {
                                                ops = ops.then(grantItemIfAbsent(state.getId(), "меч", "Острый меч для боя"));
                                            }

                                            System.out.println("DEBUG updatePlot: applying event for stateId=" + state.getId()
                                                    + " -> health=" + newHealth
                                                    + ", newLoc=" + state.getCurrentLocation()
                                                    + ", msg=" + state.getPlotProgress());

                                            return ops.then(gameStateRepository.save(state));
                                        })
                                        .doOnError(err -> System.out.println("DEBUG updatePlot ERROR: " + err));
                            });
                })
                .log("GameService.updatePlot"); // реакт-трейс в логи
    }

    private Event decide(String loc, String choiceKey, boolean hasSword, boolean hasArtifact, boolean hasLightBlade) {
        return switch (loc) {
            case LOC_FOREST -> switch (choiceKey) {
                case "go_castle" -> new Event(
                        "Вы подошли к замку. У входа — дракон. Если у вас есть меч/клинок — сражайтесь, иначе попробуйте решить загадку.",
                        0, null, LOC_CASTLE, false, false);
                case "search_treasure" -> {
                    int roll = random.nextInt(10);
                    if (roll >= 7) {
                        yield new Event("Вы нашли меч! Но волк нападает. Здоровье -20.", -20, new Item("меч", "Острый меч для боя"), LOC_FOREST, false, false);
                    } else if (roll >= 5) {
                        yield new Event("Вы нашли траву с сильным ароматом. Похоже, из неё можно сварить зелье.", 0, new Item("трава", "Ингредиент для зелья"), LOC_FOREST, false, false);
                    } else if (roll >= 3) {
                        yield new Event("Вы нашли пустую флягу. Пригодится для алхимии.", 0, new Item("фляга", "Ингредиент для зелья"), LOC_FOREST, false, false);
                    } else {
                        yield new Event("Вы нашли зелье! Здоровье +30.", +30, new Item("зелье", "Зелье лечения"), LOC_FOREST, false, false);
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
                        yield new Event("У вас нет оружия! Дракон ранит вас. Здоровье -50. Попробуйте решить загадку или найти/скрафтить оружие.",
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

    /** Доступные рецепты крафта: теперь Flux. */
    public Flux<CraftRecipe> getAvailableCrafts(Long gameStateId) {
        if (gameStateId == null) {
            return Flux.empty();
        }

        return listInventoryNames(gameStateId) // уже lower-case
                .collectList()
                .defaultIfEmpty(List.of())
                .flatMapMany(itemsLower -> {
                    List<CraftRecipe> res = new ArrayList<>();
                    for (CraftRecipe r : RECIPES.values()) {
                        if (r == null) continue;

                        String resultLower = r.result() == null || r.result().name() == null
                                ? ""
                                : r.result().name().toLowerCase(Locale.ROOT);

                        if (itemsLower.contains(resultLower)) continue;

                        boolean ok = r.requires() != null &&
                                r.requires().stream()
                                        .filter(Objects::nonNull)
                                        .map(req -> req.toLowerCase(Locale.ROOT))
                                        .allMatch(itemsLower::contains);

                        if (ok) res.add(r);
                    }
                    return Flux.fromIterable(res);
                })
                .onErrorResume(e -> Flux.empty());
    }

    public Mono<GameState> craft(Long gameStateId, String recipeKey) {
        CraftRecipe recipe = RECIPES.get(recipeKey);
        if (recipe == null) return findState(gameStateId);

        return findState(gameStateId).flatMap(state ->
                listInventoryNames(state.getId()).collectList().flatMap(itemsLower -> {
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
                        remove = remove.then(
                                removeOneItemByName(state.getId(), req).onErrorResume(__ -> Mono.empty())
                        );
                    }
                    Item out = recipe.result();
                    return remove
                            .then(grantItemIfAbsent(state.getId(), out.name(), out.description()))
                            .then(Mono.defer(() -> {
                                state.setPlotProgress("Вы скрафтили: " + out.name() + ". " + state.getPlotProgress());
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
                            (state.getHealth() != 0 ? state.getHealth() : 0) + 30,
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

    private String normalizeChoice(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return CHOICE_MAP.getOrDefault(key, key);
    }

    private String humanLocationName(String locKey) {
        return switch (locKey) {
            case LOC_FOREST -> "лес";
            case LOC_CASTLE -> "замок";
            case LOC_CAVE   -> "пещера";
            case LOC_VILLAGE-> "деревня";
            default -> "неизвестно";
        };
    }

    private String locKey(String currentLocationHuman) {
        if (currentLocationHuman == null) return LOC_FOREST;
        String s = currentLocationHuman.toLowerCase(Locale.ROOT);
        if (s.contains("лес")) return LOC_FOREST;
        if (s.contains("замок")) return LOC_CASTLE;
        if (s.contains("пещера")) return LOC_CAVE;
        if (s.contains("деревня")) return LOC_VILLAGE;
        return LOC_FOREST;
    }

    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private record Event(String message, int deltaHealth, Item itemToGrant, String newLocation,
                         boolean removeArtifact, boolean grantSwordIfMissing) {}
    public record Item(String name, String description) {}
    public record CraftRecipe(String key, String title, List<String> requires, Item result) {}
}