-- Чистим таблицы на всякий случай (для повторных запусков)
DELETE FROM inventory_items;
DELETE FROM game_states;
DELETE FROM users;

-- Пользователи (FK нет, но поле user_id у game_states должно к чему-то относиться логически)
INSERT INTO users (id, email, password) VALUES (100, 'test1@example.com', 'pwd');
INSERT INTO users (id, email, password) VALUES (200, 'test2@example.com', 'pwd');

-- Состояние игры, которое ждёт GameControllerWebTest (gameStateId=5)
INSERT INTO game_states (id, user_id, current_location, plot_progress, health)
VALUES (5, 100, 'деревня', 'Вы стоите у края деревни.', 100);

-- Состояние игры и предмет, которые ждёт InventoryControllerWebTest (gameId=9, itemId=7)
INSERT INTO game_states (id, user_id, current_location, plot_progress, health)
VALUES (9, 200, 'пещера', 'Темно и сыро.', 80);

INSERT INTO inventory_items (id, game_state_id, name, description)
VALUES (7, 9, 'зелье', 'Лечебное зелье');
