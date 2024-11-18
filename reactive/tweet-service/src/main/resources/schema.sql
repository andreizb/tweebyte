CREATE TABLE IF NOT EXISTS hashtags (
    id UUID PRIMARY KEY,
    text VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS tweets (
      id UUID PRIMARY KEY,
      user_id UUID NOT NULL,
      version BIGINT,
      content TEXT NOT NULL,
      created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_user_id ON tweets (user_id);

CREATE TABLE IF NOT EXISTS mentions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    text VARCHAR(255) NOT NULL,
    tweet_id UUID NOT NULL,
    FOREIGN KEY (tweet_id) REFERENCES tweets(id)
);

CREATE TABLE IF NOT EXISTS tweet_hashtag (
     tweet_id UUID NOT NULL,
     hashtag_id UUID NOT NULL,
     PRIMARY KEY (tweet_id, hashtag_id),
     FOREIGN KEY (tweet_id) REFERENCES tweets(id),
     FOREIGN KEY (hashtag_id) REFERENCES hashtags(id)
);
