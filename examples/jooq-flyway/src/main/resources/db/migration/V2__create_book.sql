CREATE TABLE book (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    isbn        VARCHAR(20)  NOT NULL UNIQUE,
    author_id   BIGINT       NOT NULL REFERENCES author(id) ON DELETE CASCADE,
    published   DATE,
    price       NUMERIC(10, 2),
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_book_author ON book(author_id);
CREATE INDEX idx_book_isbn   ON book(isbn);
