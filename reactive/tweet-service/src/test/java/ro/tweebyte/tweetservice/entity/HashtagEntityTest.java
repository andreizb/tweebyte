package ro.tweebyte.tweetservice.entity;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

public class HashtagEntityTest {

    @Test
    public void testBuilder() {
        UUID id = UUID.randomUUID();
        HashtagEntity hashtagEntity = HashtagEntity.builder()
            .id(id)
            .text("#example")
            .isInsertable(true)
            .build();

        assertThat(hashtagEntity.getId()).isEqualTo(id);
        assertThat(hashtagEntity.getText()).isEqualTo("#example");
        assertThat(hashtagEntity.isNew()).isTrue();
    }

    @Test
    public void testNoArgsConstructor() {
        HashtagEntity hashtagEntity = new HashtagEntity();

        assertThat(hashtagEntity.getId()).isNull();
        assertThat(hashtagEntity.getText()).isNull();
        assertThat(hashtagEntity.isInsertable()).isFalse();
    }

    @Test
    public void testAllArgsConstructor() {
        UUID id = UUID.randomUUID();
        HashtagEntity hashtagEntity = new HashtagEntity(id, "#example", true);

        assertThat(hashtagEntity.getId()).isEqualTo(id);
        assertThat(hashtagEntity.getText()).isEqualTo("#example");
        assertThat(hashtagEntity.isInsertable()).isTrue();
    }

    @Test
    public void testSettersAndGetters() {
        HashtagEntity hashtagEntity = new HashtagEntity();
        UUID id = UUID.randomUUID();
        hashtagEntity.setId(id);
        hashtagEntity.setText("#example");
        hashtagEntity.setInsertable(true);

        assertThat(hashtagEntity.getId()).isEqualTo(id);
        assertThat(hashtagEntity.getText()).isEqualTo("#example");
        assertThat(hashtagEntity.isInsertable()).isTrue();
    }

    @Test
    public void testIsNewWithNullId() {
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setInsertable(false);
        assertThat(hashtagEntity.isNew()).isTrue();
    }

    @Test
    public void testIsNewWithNonNullId() {
        UUID id = UUID.randomUUID();
        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setId(id);
        hashtagEntity.setInsertable(false);

        assertThat(hashtagEntity.isNew()).isFalse();
    }
}