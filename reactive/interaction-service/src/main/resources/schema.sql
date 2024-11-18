CREATE TABLE IF NOT EXISTS follows (
       id UUID PRIMARY KEY,
       follower_id UUID NOT NULL,
       followed_id UUID NOT NULL,
       status VARCHAR(255),
    created_at TIMESTAMP NOT NULL
    UNIQUE(follower_id, followed_id),
    FOREIGN KEY (id) REFERENCES interactions(id)
);

CREATE TABLE IF NOT EXISTS likes (
     id UUID PRIMARY KEY,
     user_id UUID NOT NULL,
     likeable_id UUID NOT NULL,
     likeable_type VARCHAR(255),
    created_at TIMESTAMP NOT NULL
    UNIQUE(user_id, likeable_id, likeable_type),
    FOREIGN KEY (id) REFERENCES interactions(id)
);

CREATE TABLE IF NOT EXISTS replies (
       id UUID PRIMARY KEY,
       tweet_id UUID NOT NULL,
       user_id UUID NOT NULL,
       content TEXT,
       created_at TIMESTAMP NOT NULL
       FOREIGN KEY (id) REFERENCES interactions(id)
);

CREATE TABLE IF NOT EXISTS retweets (
    id UUID PRIMARY KEY,
    original_tweet_id UUID NOT NULL,
    retweeter_id UUID NOT NULL,
    content TEXT,
    created_at TIMESTAMP NOT NULL
    FOREIGN KEY (id) REFERENCES interactions(id)
);
