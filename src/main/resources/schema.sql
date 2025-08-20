CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     email VARCHAR(255) UNIQUE NOT NULL,
                                     password VARCHAR(255) NOT NULL
);
CREATE TABLE IF NOT EXISTS game_states (
                                           id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                           user_id BIGINT NOT NULL,
                                           current_location VARCHAR(255),
                                           plot_progress TEXT,
                                           health INT
);
CREATE TABLE IF NOT EXISTS inventory_items (
                                               id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                               game_state_id BIGINT NOT NULL,
                                               name VARCHAR(255),
                                               description TEXT
);