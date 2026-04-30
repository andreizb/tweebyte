package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.model.LikeDto;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Branch-coverage tests for LikeMapper — exercises:
 *  - mapCreationRequestToEntity each clause of the &amp;&amp; null-guard going both ways
 *  - mapToDto(LikeEntity, UserDto) with null entity (likeDto == null branch)
 *  - mapToDto(LikeEntity, TweetDto) with null entity (likeDto == null branch)
 */
class LikeMapperBranchTest {

    private final LikeMapper likeMapper = new LikeMapper();

    @Test
    void mapCreationRequestToEntity_userIdNonNull_returnsEntity() {
        // userId != null short-circuits the first &&, exercising the false arm.
        LikeEntity entity = likeMapper.mapCreationRequestToEntity(UUID.randomUUID(), null, null);
        assertNotNull(entity);
    }

    @Test
    void mapCreationRequestToEntity_likeableIdNonNull_returnsEntity() {
        // userId null, likeableId != null → second && false arm.
        LikeEntity entity = likeMapper.mapCreationRequestToEntity(null, UUID.randomUUID(), null);
        assertNotNull(entity);
    }

    @Test
    void mapCreationRequestToEntity_likeableTypeNonNull_returnsEntity() {
        // userId null, likeableId null, likeableType != null → third && false arm.
        LikeEntity entity = likeMapper.mapCreationRequestToEntity(null, null, LikeEntity.LikeableType.TWEET);
        assertNotNull(entity);
    }

    @Test
    void mapToDtoUserOverload_nullEntity_returnsNull() {
        // mapEntityToDto returns null → guard skips setUser and returns null.
        UserDto userDto = new UserDto();
        LikeDto result = likeMapper.mapToDto((LikeEntity) null, userDto);
        assertNull(result);
    }

    @Test
    void mapToDtoTweetOverload_nullEntity_returnsNull() {
        // mapEntityToDto returns null → guard skips setTweet and returns null.
        TweetDto tweetDto = new TweetDto();
        LikeDto result = likeMapper.mapToDto((LikeEntity) null, tweetDto);
        assertNull(result);
    }
}
