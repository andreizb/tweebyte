package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.client.TweetClient;
import ro.tweebyte.interactionservice.model.TweetDto;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TweetService {

    private static final String TWEET_SUMMARY_KEY_PREFIX = "tweets:";
    private static final String USER_TWEETS_SUMMARY_KEY_PREFIX = "user_tweets:";
    private static final String POPULAR_HASHTAGS_KEY = "popular_hashtags:";

    private final TweetClient tweetClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public Mono<TweetDto> getTweetSummary(UUID tweetId) {
        String key = TWEET_SUMMARY_KEY_PREFIX + tweetId;
        return redisTemplate.opsForValue().get(key)
                .cast(TweetDto.class)
                .switchIfEmpty(tweetClient.getTweetSummary(tweetId)
                        .doOnNext(tweetDto -> redisTemplate.opsForValue().set(key, tweetDto)));
    }

    public Flux<TweetDto> getUserTweetsSummary(UUID userId) {
        String key = USER_TWEETS_SUMMARY_KEY_PREFIX + userId;
        return redisTemplate.opsForList().range(key, 0, -1)
                .cast(TweetDto.class)
                .switchIfEmpty(tweetClient.getUserTweetsSummary(userId)
                        .doOnNext(tweetDto -> redisTemplate.opsForList().rightPush(key, tweetDto))
                        .thenMany(redisTemplate.opsForList().range(key, 0, -1).cast(TweetDto.class)));
    }

    public Flux<TweetDto.HashtagDto> getPopularHashtags() {
        return redisTemplate.opsForList().range(POPULAR_HASHTAGS_KEY, 0, -1)
                .cast(TweetDto.HashtagDto.class)
                .switchIfEmpty(tweetClient.getPopularHashtags()
                        .doOnNext(hashtagDto -> redisTemplate.opsForList().rightPush(POPULAR_HASHTAGS_KEY, hashtagDto))
                        .thenMany(redisTemplate.opsForList().range(POPULAR_HASHTAGS_KEY, 0, -1).cast(TweetDto.HashtagDto.class)));
    }

}
