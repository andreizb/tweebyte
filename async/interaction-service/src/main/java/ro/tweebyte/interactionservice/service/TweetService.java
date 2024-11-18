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

    @Cacheable(value = "tweets", key = "#tweetId", unless = "#result == null")
    public TweetDto getTweetSummary(UUID tweetId) {
        return tweetClient.getTweetSummary(tweetId);
    }

    @Cacheable(value = "user_tweets", key = "#userId", unless = "#result.isEmpty()")
    public List<TweetDto> getUserTweetsSummary(UUID userId) {
        return tweetClient.getUserTweetsSummary(userId);
    }

    @Cacheable(value = "popular_hashtags", unless = "result.isEmpty()")
    public List<TweetDto.HashtagDto> getPopularHashtags() {
        return tweetClient.getPopularHashtags();
    }

}
