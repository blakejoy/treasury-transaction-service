CREATE TABLE transaction (
    id               UUID           PRIMARY KEY,
    description      VARCHAR(50)    NOT NULL,
    amount_usd       NUMERIC(19, 2) NOT NULL,
    transaction_date DATE           NOT NULL
);
