package ro.tweebyte.tweetservice.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TweetHashtagEntityTest {

    @Test
    public void testBuilder() {
        UUID tweetId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();
        TweetHashtagEntity tweetHashtagEntity = TweetHashtagEntity.builder()
            .tweetId(tweetId)
            .hashtagId(hashtagId)
            .build();

        assertThat(tweetHashtagEntity.getTweetId()).isEqualTo(tweetId);
        assertThat(tweetHashtagEntity.getHashtagId()).isEqualTo(hashtagId);
    }

    @Test
    public void testNoArgsConstructor() {
        TweetHashtagEntity tweetHashtagEntity = new TweetHashtagEntity();

        assertThat(tweetHashtagEntity.getTweetId()).isNull();
        assertThat(tweetHashtagEntity.getHashtagId()).isNull();
    }

    @Test
    public void testAllArgsConstructor() {
        UUID tweetId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();
        TweetHashtagEntity tweetHashtagEntity = new TweetHashtagEntity(tweetId, hashtagId);

        assertThat(tweetHashtagEntity.getTweetId()).isEqualTo(tweetId);
        assertThat(tweetHashtagEntity.getHashtagId()).isEqualTo(hashtagId);
    }

    @Test
    public void testSetters() {
        TweetHashtagEntity tweetHashtagEntity = new TweetHashtagEntity();
        UUID tweetId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();

        tweetHashtagEntity.setTweetId(tweetId);
        tweetHashtagEntity.setHashtagId(hashtagId);

        assertThat(tweetHashtagEntity.getTweetId()).isEqualTo(tweetId);
        assertThat(tweetHashtagEntity.getHashtagId()).isEqualTo(hashtagId);
    }

}