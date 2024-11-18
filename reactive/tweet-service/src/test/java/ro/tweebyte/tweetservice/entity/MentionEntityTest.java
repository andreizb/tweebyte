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
}
