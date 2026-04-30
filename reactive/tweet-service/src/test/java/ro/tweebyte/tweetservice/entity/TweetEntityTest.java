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

    @Test
    public void getId() {
        UUID id = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setId(id);
        assertThat(tweetEntity.getId()).isEqualTo(id);
    }

    @Test
    public void getUserId() {
        UUID userId = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setUserId(userId);
        assertThat(tweetEntity.getUserId()).isEqualTo(userId);
    }

    @Test
    public void getVersion() {
        Long version = 1L;
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setVersion(version);
        assertThat(tweetEntity.getVersion()).isEqualTo(version);
    }

    @Test
    public void getContent() {
        String content = "Tweet content";
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setContent(content);
        assertThat(tweetEntity.getContent()).isEqualTo(content);
    }

    @Test
    public void getCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setCreatedAt(createdAt);
        assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    public void setId() {
        UUID id = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        assertThat(tweetEntity.getId()).isNull();
        tweetEntity.setId(id);
        assertThat(tweetEntity.getId()).isEqualTo(id);
    }

    @Test
    public void setUserId() {
        UUID userId = UUID.randomUUID();
        TweetEntity tweetEntity = new TweetEntity();
        assertThat(tweetEntity.getUserId()).isNull();
        tweetEntity.setUserId(userId);
        assertThat(tweetEntity.getUserId()).isEqualTo(userId);
    }

    @Test
    public void setVersion() {
        Long version = 1L;
        TweetEntity tweetEntity = new TweetEntity();
        assertThat(tweetEntity.getVersion()).isNull();
        tweetEntity.setVersion(version);
        assertThat(tweetEntity.getVersion()).isEqualTo(version);
    }

    @Test
    public void setContent() {
        String content = "Tweet content";
        TweetEntity tweetEntity = new TweetEntity();
        assertThat(tweetEntity.getContent()).isNull();
        tweetEntity.setContent(content);
        assertThat(tweetEntity.getContent()).isEqualTo(content);
    }

    @Test
    public void setCreatedAt() {
        LocalDateTime createdAt = LocalDateTime.now();
        TweetEntity tweetEntity = new TweetEntity();
        assertThat(tweetEntity.getCreatedAt()).isNull();
        tweetEntity.setCreatedAt(createdAt);
        assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    public void builderTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test tweet content";
        LocalDateTime createdAt = LocalDateTime.now();
        Long version = 0L;

        TweetEntity tweetEntity = TweetEntity.builder()
            .id(id)
            .userId(userId)
            .version(version)
            .content(content)
            .createdAt(createdAt)
            .build();

        assertThat(tweetEntity).isNotNull();
        assertThat(tweetEntity.getId()).isEqualTo(id);
        assertThat(tweetEntity.getUserId()).isEqualTo(userId);
        assertThat(tweetEntity.getContent()).isEqualTo(content);
        assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(tweetEntity.getVersion()).isEqualTo(version);
    }

    @Test
    public void allArgsConstructorTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "Test tweet content";
        LocalDateTime createdAt = LocalDateTime.now();

        TweetEntity tweetEntity = new TweetEntity(id, userId, 0L, content, createdAt);

        assertThat(tweetEntity).isNotNull();
        assertThat(tweetEntity.getId()).isEqualTo(id);
        assertThat(tweetEntity.getUserId()).isEqualTo(userId);
        assertThat(tweetEntity.getContent()).isEqualTo(content);
        assertThat(tweetEntity.getCreatedAt()).isEqualTo(createdAt);
        assertThat(tweetEntity.getVersion()).isEqualTo(0L);
    }

    @Test
    public void noArgsConstructorTest() {
        TweetEntity tweetEntity = new TweetEntity();
        assertThat(tweetEntity.getId()).isNull();
        assertThat(tweetEntity.getUserId()).isNull();
        assertThat(tweetEntity.getContent()).isNull();
        assertThat(tweetEntity.getCreatedAt()).isNull();
        assertThat(tweetEntity.getVersion()).isNull();
    }

    @Test
    public void versionIncrementBehavior() {
        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setVersion(1L);
        assertThat(tweetEntity.getVersion()).isEqualTo(1L);
        tweetEntity.setVersion(2L);
        assertThat(tweetEntity.getVersion()).isEqualTo(2L);
        tweetEntity.setVersion(null);
        assertThat(tweetEntity.getVersion()).isNull();
    }
}
