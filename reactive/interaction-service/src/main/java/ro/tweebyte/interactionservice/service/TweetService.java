package ro.tweebyte.interactionservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        private final ReactiveRedisTemplate<String, byte[]> redisTemplate;
        // findAndRegisterModules() picks up jackson-datatype-jsr310 (LocalDateTime
        // / LocalDate / Instant). Without it Jackson raises
        // "Java 8 date/time type `java.time.LocalDateTime` not supported by default"
        // on every UserDto / TweetDto serialization.
        private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        public Mono<TweetDto> getTweetSummary(UUID tweetId) {
                String key = TWEET_SUMMARY_KEY_PREFIX + tweetId;
                return redisTemplate.opsForValue().get(key)
                                .flatMap(bytes -> {
                                        try {
                                                return Mono.just(objectMapper.readValue(bytes, TweetDto.class));
                                        } catch (Exception e) {
                                                return Mono.error(e);
                                        }
                                })
                                .switchIfEmpty(tweetClient.getTweetSummary(tweetId)
                                                .flatMap(tweetDto -> {
                                                        try {
                                                                return redisTemplate.opsForValue().set(key,
                                                                                objectMapper.writeValueAsBytes(
                                                                                                tweetDto))
                                                                                .thenReturn(tweetDto);
                                                        } catch (Exception e) {
                                                                return Mono.error(e);
                                                        }
                                                }));
        }

        public Flux<TweetDto> getUserTweetsSummary(UUID userId) {
                String key = USER_TWEETS_SUMMARY_KEY_PREFIX + userId;
                return redisTemplate.opsForList().range(key, 0, -1)
                                .flatMap(bytes -> {
                                        try {
                                                return Mono.just(objectMapper.readValue(bytes, TweetDto.class));
                                        } catch (Exception e) {
                                                return Mono.error(e);
                                        }
                                })
                                .switchIfEmpty(tweetClient.getUserTweetsSummary(userId)
                                                .flatMap(tweetDto -> {
                                                        try {
                                                                return redisTemplate.opsForList().rightPush(key,
                                                                                objectMapper.writeValueAsBytes(
                                                                                                tweetDto))
                                                                                .thenReturn(tweetDto);
                                                        } catch (Exception e) {
                                                                return Mono.error(e);
                                                        }
                                                }));
        }

        public Flux<TweetDto.HashtagDto> getPopularHashtags() {
                return redisTemplate.opsForList().range(POPULAR_HASHTAGS_KEY, 0, -1)
                                .flatMap(bytes -> {
                                        try {
                                                return Mono.just(objectMapper.readValue(bytes,
                                                                TweetDto.HashtagDto.class));
                                        } catch (Exception e) {
                                                return Mono.error(e);
                                        }
                                })
                                .switchIfEmpty(tweetClient.getPopularHashtags()
                                                .flatMap(hashtagDto -> {
                                                        try {
                                                                return redisTemplate.opsForList().rightPush(
                                                                                POPULAR_HASHTAGS_KEY,
                                                                                objectMapper.writeValueAsBytes(
                                                                                                hashtagDto))
                                                                                .thenReturn(hashtagDto);
                                                        } catch (Exception e) {
                                                                return Mono.error(e);
                                                        }
                                                }));
        }

}
