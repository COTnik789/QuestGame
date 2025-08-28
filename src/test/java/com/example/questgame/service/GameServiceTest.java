package com.example.questgame.service;

import com.example.questgame.exception.NotFoundException;
import com.example.questgame.model.GameState;
import com.example.questgame.model.InventoryItem;
import com.example.questgame.repository.GameStateRepository;
import com.example.questgame.repository.InventoryItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock GameStateRepository gameStateRepository;
    @Mock InventoryItemRepository inventoryItemRepository;

    @InjectMocks GameService service;

    private GameState state;

    @BeforeEach
    void setup() {
        state = new GameState();
        state.setId(1L);
        state.setUserId(777L);
        state.setCurrentLocation("пещера"); // удобно для загадки
        state.setPlotProgress("В пещере темно.");
        state.setHealth(90);
    }

    @Test
    void findState_notFound_throws() {
        given(gameStateRepository.findById(1L)).willReturn(Mono.empty());

        StepVerifier.create(service.findState(1L))
                .expectErrorSatisfies(e -> assertThat(e).isInstanceOf(NotFoundException.class))
                .verify();
    }

    @Test
    void answerRiddle_correct_grantsArtifactAndSaves() {
        given(gameStateRepository.findById(1L)).willReturn(Mono.just(state));
        given(gameStateRepository.save(any())).willAnswer(i -> Mono.just(i.getArgument(0)));
        lenient().when(inventoryItemRepository.findFirstByGameStateIdAndNameIgnoreCase(1L, "артефакт"))
                .thenReturn(Mono.empty());
        given(inventoryItemRepository.save(any())).willAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(service.answerRiddle(1L, "СЫР"))
                .assertNext(saved -> assertThat(saved.getPlotProgress()).contains("Верно!"))
                .verifyComplete();

        verify(inventoryItemRepository).save(argThat(it ->
                it.getGameStateId().equals(1L) && "артефакт".equalsIgnoreCase(it.getName())));
    }

    @Test
    void answerRiddle_wrong_hitsHealthAndSaves() {
        given(gameStateRepository.findById(1L)).willReturn(Mono.just(state));
        given(gameStateRepository.save(any())).willAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(service.answerRiddle(1L, "лук"))
                .assertNext(saved -> {
                    assertThat(saved.getHealth()).isEqualTo(60); // 90 - 30
                    assertThat(saved.getPlotProgress()).contains("Неверно");
                })
                .verifyComplete();
    }

    @Test
    void craft_lightBlade_consumesInputs_andGrantsResult() {
        state.setCurrentLocation("лес");
        given(gameStateRepository.findById(1L)).willReturn(Mono.just(state));
        given(gameStateRepository.save(any())).willAnswer(i -> Mono.just(i.getArgument(0)));

        given(inventoryItemRepository.findByGameStateId(1L)).willReturn(Flux.fromIterable(List.of(
                item(11L, 1L, "меч"),
                item(12L, 1L, "артефакт")
        )));
        given(inventoryItemRepository.findFirstByGameStateIdAndNameIgnoreCase(1L,"меч"))
                .willReturn(Mono.just(item(11L,1L,"меч")));
        given(inventoryItemRepository.findFirstByGameStateIdAndNameIgnoreCase(1L,"артефакт"))
                .willReturn(Mono.just(item(12L,1L,"артефакт")));
        lenient().when(inventoryItemRepository.findFirstByGameStateIdAndNameIgnoreCase(1L,"клинок света"))
                .thenReturn(Mono.empty());

        given(inventoryItemRepository.deleteById(anyLong())).willReturn(Mono.empty());
        given(inventoryItemRepository.save(any())).willAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(service.craft(1L, "light_blade"))
                .assertNext(saved -> assertThat(saved.getPlotProgress()).contains("Вы создали: клинок света"))
                .verifyComplete();

        verify(inventoryItemRepository).deleteById(11L);
        verify(inventoryItemRepository).deleteById(12L);
        verify(inventoryItemRepository).save(argThat(i -> "клинок света".equalsIgnoreCase(i.getName())));
    }

    @Test
    void useItem_potion_increasesHealthAndDeletes() {
        state.setHealth(75);
        given(gameStateRepository.findById(1L)).willReturn(Mono.just(state));
        given(gameStateRepository.save(any())).willAnswer(i -> Mono.just(i.getArgument(0)));

        InventoryItem potion = item(50L, 1L, "зелье");
        given(inventoryItemRepository.findById(50L)).willReturn(Mono.just(potion));
        given(inventoryItemRepository.deleteById(50L)).willReturn(Mono.empty());

        StepVerifier.create(service.useItem(1L, 50L))
                .assertNext(saved -> assertThat(saved.getHealth()).isEqualTo(100))
                .verifyComplete();
    }

    @Test
    void restart_clearsInventory_andResetsState() {
        given(gameStateRepository.findById(1L)).willReturn(Mono.just(state));
        given(inventoryItemRepository.findByGameStateId(1L))
                .willReturn(Flux.fromIterable(List.of(item(1L,1L,"меч"), item(2L,1L,"зелье"))));
        given(inventoryItemRepository.deleteById(anyLong())).willReturn(Mono.empty());
        given(gameStateRepository.save(any())).willAnswer(i -> Mono.just(i.getArgument(0)));

        StepVerifier.create(service.restartGame(1L))
                .assertNext(saved -> {
                    assertThat(saved.getHealth()).isEqualTo(100);
                    assertThat(saved.getCurrentLocation()).contains("лес");
                })
                .verifyComplete();
    }

    private static InventoryItem item(Long id, Long gsId, String name) {
        InventoryItem i = new InventoryItem();
        i.setId(id);
        i.setGameStateId(gsId);
        i.setName(name);
        i.setDescription(name);
        return i;
    }
}
