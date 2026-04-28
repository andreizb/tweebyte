package ro.tweebyte.tweetservice.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LikeDtoTest {

    @Test
    void testGetId() {
        UUID id = UUID.randomUUID();
        LikeDto likeDto = new LikeDto().setId(id);
        assertEquals(id, likeDto.getId());
    }

    @Test
    void testSetId() {
        UUID id = UUID.randomUUID();
        LikeDto likeDto = new LikeDto();
        likeDto.setId(id);
        assertEquals(id, likeDto.getId());
    }

}