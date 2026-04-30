package ro.tweebyte.tweetservice.entity;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

public class MentionEntityTest {

    @Test
    public void testMentionEntity() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID tweetId = UUID.randomUUID();
        MentionEntity mentionEntity = MentionEntity.builder()
            .id(id)
            .userId(userId)
            .text("@user")
            .tweetId(tweetId)
            .isInsertable(true)
            .build();

        assertThat(mentionEntity.getId()).isEqualTo(id);
        assertThat(mentionEntity.getUserId()).isEqualTo(userId);
        assertThat(mentionEntity.getText()).isEqualTo("@user");
        assertThat(mentionEntity.getTweetId()).isEqualTo(tweetId);
        assertThat(mentionEntity.isNew()).isTrue();

        mentionEntity.setInsertable(false);
        assertThat(mentionEntity.isNew()).isFalse();
        assertThat(mentionEntity.isInsertable()).isFalse();

        mentionEntity.setId(null);
        assertThat(mentionEntity.isNew()).isTrue();
        assertThat(mentionEntity.isInsertable()).isFalse();
    }

    @Test
    public void getId() {
        UUID id = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setId(id);
        assertThat(mentionEntity.getId()).isEqualTo(id);
    }

    @Test
    public void getUserId() {
        UUID userId = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setUserId(userId);
        assertThat(mentionEntity.getUserId()).isEqualTo(userId);
    }

    @Test
    public void getText() {
        String text = "mentionText";
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setText(text);
        assertThat(mentionEntity.getText()).isEqualTo(text);
    }

    @Test
    public void getTweetId() {
        UUID tweetId = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        mentionEntity.setTweetId(tweetId);
        assertThat(mentionEntity.getTweetId()).isEqualTo(tweetId);
    }

    @Test
    public void setId() {
        UUID id = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        assertThat(mentionEntity.getId()).isNull();
        mentionEntity.setId(id);
        assertThat(mentionEntity.getId()).isEqualTo(id);
    }

    @Test
    public void setUserId() {
        UUID userId = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        assertThat(mentionEntity.getUserId()).isNull();
        mentionEntity.setUserId(userId);
        assertThat(mentionEntity.getUserId()).isEqualTo(userId);
    }

    @Test
    public void setText() {
        String text = "mentionText";
        MentionEntity mentionEntity = new MentionEntity();
        assertThat(mentionEntity.getText()).isNull();
        mentionEntity.setText(text);
        assertThat(mentionEntity.getText()).isEqualTo(text);
    }

    @Test
    public void setTweetId() {
        UUID tweetId = UUID.randomUUID();
        MentionEntity mentionEntity = new MentionEntity();
        assertThat(mentionEntity.getTweetId()).isNull();
        mentionEntity.setTweetId(tweetId);
        assertThat(mentionEntity.getTweetId()).isEqualTo(tweetId);
    }

    @Test
    public void builderTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String text = "@testUser";
        UUID tweetId = UUID.randomUUID();

        MentionEntity mentionEntity = MentionEntity.builder()
            .id(id)
            .userId(userId)
            .text(text)
            .tweetId(tweetId)
            .build();

        assertThat(mentionEntity).isNotNull();
        assertThat(mentionEntity.getId()).isEqualTo(id);
        assertThat(mentionEntity.getUserId()).isEqualTo(userId);
        assertThat(mentionEntity.getText()).isEqualTo(text);
        assertThat(mentionEntity.getTweetId()).isEqualTo(tweetId);
    }

    @Test
    public void allArgsConstructorTest() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String text = "@testUser";
        UUID tweetId = UUID.randomUUID();

        MentionEntity mentionEntity = new MentionEntity(id, userId, text, tweetId, false);

        assertThat(mentionEntity).isNotNull();
        assertThat(mentionEntity.getId()).isEqualTo(id);
        assertThat(mentionEntity.getUserId()).isEqualTo(userId);
        assertThat(mentionEntity.getText()).isEqualTo(text);
        assertThat(mentionEntity.getTweetId()).isEqualTo(tweetId);
        assertThat(mentionEntity.isInsertable()).isFalse();
    }
}
