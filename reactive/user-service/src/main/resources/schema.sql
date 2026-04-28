CREATE TABLE users (
    id UUID PRIMARY KEY,
    user_name VARCHAR(255),
    email VARCHAR(255) UNIQUE NOT NULL,
    biography VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    is_private BOOLEAN NOT NULL,
    birth_date DATE,
    created_at TIMESTAMP
);