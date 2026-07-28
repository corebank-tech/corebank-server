CREATE TABLE IF NOT EXISTS transaction_type (
    code VARCHAR(30) NOT NULL,
    name VARCHAR(50) NOT NULL,
    sign INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (code)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
