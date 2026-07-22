CREATE TABLE author (
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name  VARCHAR(100) NOT NULL,
    bio        TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
