package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Branch-coverage tests for the four mappers — exercises the null-input
 * guard arms that the existing happy-path mapper tests skip.
 */
class MapperBranchTest {

    private final RetweetMapper retweetMapper = new RetweetMapper();
    private final ReplyMapper replyMapper = new ReplyMapper();
    private final LikeMapper likeMapper = new LikeMapper();
    private final FollowMapper followMapper = new FollowMapper();

    // ---------- RetweetMapper ----------

    @Test
    void retweet_mapEntityToDto_nullEntity_returnsNull() {
        assertNull(retweetMapper.mapEntityToDto(null));
        assertNull(retweetMapper.mapEntityToDto(null, new UserDto()));
        assertNull(retweetMapper.mapEntityToDto(null, new UserDto(), new TweetDto()));
    }

    @Test
    void retweet_mapRequestToEntity_nullRequest_noop() {
        RetweetEntity entity = new RetweetEntity();
        entity.setContent("orig");
        retweetMapper.mapRequestToEntity((RetweetUpdateRequest) null, entity);
        assertEquals("orig", entity.getContent());
    }

    @Test
    void retweet_mapRequestToEntity_nullEntity_noop() {
        RetweetUpdateRequest req = new RetweetUpdateRequest();
        req.setContent("new");
        // Should not throw.
        retweetMapper.mapRequestToEntity(req, null);
    }

    @Test
    void retweet_mapRequestToEntity_nullContentLeavesEntity() {
        RetweetUpdateRequest req = new RetweetUpdateRequest();
        // content stays null
        req.setRetweeterId(UUID.randomUUID());
        RetweetEntity entity = new RetweetEntity();
        entity.setContent("original");
        retweetMapper.mapRequestToEntity(req, entity);
        assertEquals("original", entity.getContent());
        assertEquals(req.getRetweeterId(), entity.getRetweeterId());
    }

    @Test
    void retweet_mapRequestToEntity_nullRetweeterIdLeavesEntity() {
        RetweetUpdateRequest req = new RetweetUpdateRequest();
        req.setContent("c");
        // retweeterId stays null
        UUID original = UUID.randomUUID();
        RetweetEntity entity = new RetweetEntity();
        entity.setRetweeterId(original);
        retweetMapper.mapRequestToEntity(req, entity);
        assertEquals(original, entity.getRetweeterId());
        assertEquals("c", entity.getContent());
    }

    // ---------- ReplyMapper ----------

    @Test
    void reply_mapEntityToCreationDto_null_returnsNull() {
        assertNull(replyMapper.mapEntityToCreationDto(null));
    }

    @Test
    void reply_mapEntityToDto_null_returnsNull() {
        assertNull(replyMapper.mapEntityToDto(null, "name"));
    }

    @Test
    void reply_mapCreationRequestToEntity_null_returnsNull() {
        assertNull(replyMapper.mapCreationRequestToEntity(null));
    }

    @Test
    void reply_mapRequestToEntity_nullRequest_noop() {
        ReplyEntity entity = new ReplyEntity();
        entity.setContent("orig");
        replyMapper.mapRequestToEntity(null, entity);
        assertEquals("orig", entity.getContent());
    }

    @Test
    void reply_mapRequestToEntity_nullEntity_noop() {
        ReplyUpdateRequest req = new ReplyUpdateRequest();
        replyMapper.mapRequestToEntity(req, null);
    }

    @Test
    void reply_mapRequestToEntity_nullContentLeavesEntity() {
        ReplyUpdateRequest req = new ReplyUpdateRequest();
        req.setUserId(UUID.randomUUID());
        ReplyEntity entity = new ReplyEntity();
        entity.setContent("orig");
        replyMapper.mapRequestToEntity(req, entity);
        assertEquals("orig", entity.getContent());
        assertEquals(req.getUserId(), entity.getUserId());
    }

    @Test
    void reply_mapRequestToEntity_nullUserIdLeavesEntity() {
        ReplyUpdateRequest req = new ReplyUpdateRequest();
        req.setContent("c");
        UUID original = UUID.randomUUID();
        ReplyEntity entity = new ReplyEntity();
        entity.setUserId(original);
        replyMapper.mapRequestToEntity(req, entity);
        assertEquals(original, entity.getUserId());
        assertEquals("c", entity.getContent());
    }

    // ---------- LikeMapper ----------

    @Test
    void like_mapEntityToDto_null_returnsNull() {
        assertNull(likeMapper.mapEntityToDto(null));
    }

    @Test
    void like_mapToDto_nullEntity_returnsNull() {
        assertNull(likeMapper.mapToDto(null, new UserDto()));
        assertNull(likeMapper.mapToDto(null, new TweetDto()));
    }

    @Test
    void like_mapCreationRequestToEntity_allNull_returnsNull() {
        assertNull(likeMapper.mapCreationRequestToEntity(null, null, null));
    }

    @Test
    void like_mapCreationRequestToEntity_someNull_returnsEntity() {
        // Negative branch of the and-chain: at least one non-null → builds entity.
        LikeEntity e = likeMapper.mapCreationRequestToEntity(UUID.randomUUID(), null, null);
        assertNotNull(e);
    }

    // ---------- FollowMapper ----------

    @Test
    void follow_mapEntityToDto_nullEntity_returnsNull() {
        assertNull(followMapper.mapEntityToDto(null));
    }

    @Test
    void follow_mapEntityToDto_nullEntity_nullName_returnsNull() {
        assertNull(followMapper.mapEntityToDto(null, null));
    }

    @Test
    void follow_mapEntityToDto_nullEntity_butWithName_returnsDto() {
        // mapEntityToDto(null, "x") — null entity but non-null name builds an empty DTO.
        FollowDto dto = followMapper.mapEntityToDto(null, "name");
        assertNotNull(dto);
        assertEquals("name", dto.getUserName());
    }

    @Test
    void follow_mapEntityToDto_nullStatus_dtoStatusNull() {
        FollowEntity e = new FollowEntity();
        e.setStatus(null);
        FollowDto dto = followMapper.mapEntityToDto(e);
        assertNotNull(dto);
        assertNull(dto.getStatus());
    }

    @Test
    void follow_mapEntityToDto_withName_setsName() {
        FollowEntity e = new FollowEntity();
        e.setStatus(FollowEntity.Status.ACCEPTED);
        FollowDto dto = followMapper.mapEntityToDto(e, "alice");
        assertNotNull(dto);
        assertEquals("alice", dto.getUserName());
        assertEquals(FollowDto.Status.ACCEPTED, dto.getStatus());
    }
}
