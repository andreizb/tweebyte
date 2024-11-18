package ro.tweebyte.tweetservice.entity;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

public class TweetEntityTest {

    @Test
    public void testTweetEntity() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();
        TweetEntity tweetEntity = TweetEntity.builder()
            .id(id)
            .userId(userId)
            .content("This is a test tweet")
            .createdAt(createdAt)
            .version(1L)
            .build();

        assertThat(tweetEntity.getId()).isEqualTo(id);
        assertThat(tweetEntity.getUserId()).isEqualTo(userId);
        assertThat(tweetEntity.getContent()).isEqualTo("This is a test tweet");
        assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(tweetEntity.getVersion()).isEqualTo(1L);

        tweetEntity.setVersion(2L);
        assertThat(tweetEntity.getVersion()).isEqualTo(2L);
    }
}
