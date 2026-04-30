package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import ro.tweebyte.interactionservice.client.TweetClient;
import ro.tweebyte.interactionservice.model.TweetDto;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TweetService {

    private final TweetClient tweetClient;

    // positional SpEL `#a0` instead of `#tweetId`/`#userId` — works regardless of
    // whether method-parameter names are emitted into bytecode. Without this, every
    // post-reply / post-retweet returned 500 ("Null key returned for cache operation")
    // because Spring's KeyGenerator couldn't resolve the named parameter.
    @Cacheable(value = "tweets", key = "#a0", unless = "#result == null")
    public TweetDto getTweetSummary(UUID tweetId) {
        return tweetClient.getTweetSummary(tweetId);
    }

    @Cacheable(value = "user_tweets", key = "#a0", unless = "#result.isEmpty()")
    public List<TweetDto> getUserTweetsSummary(UUID userId) {
        return tweetClient.getUserTweetsSummary(userId);
    }

    // SpEL `unless` reference must be `#result`, not `result` — without
    // the `#` prefix Spring Cache cannot bind the variable on `CacheExpressionRootObject`
    // and every call to /recommendations/hashtags surfaced as a 500 with
    // `EL1008E: Property or field 'result' cannot be found`. Reactive doesn't
    // hit this because its TweetService doesn't use Spring's @Cacheable here.
    @Cacheable(value = "popular_hashtags", unless = "#result.isEmpty()")
    public List<TweetDto.HashtagDto> getPopularHashtags() {
        return tweetClient.getPopularHashtags();
    }

}
