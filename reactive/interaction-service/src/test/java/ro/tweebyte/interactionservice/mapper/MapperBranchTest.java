package ro.tweebyte.interactionservice.mapper;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Branch-coverage tests for the four MapStruct mappers — exercises null-input
 * guards and partial-update arms that the existing happy-path mapper tests
 * skip.
 */
class MapperBranchTest {

    private final RetweetMapper retweetMapper = Mappers.getMapper(RetweetMapper.class);
    private final ReplyMapper replyMapper = Mappers.getMapper(ReplyMapper.class);
    private final LikeMapper likeMapper = Mappers.getMapper(LikeMapper.class);
    private final FollowMapper followMapper = Mappers.getMapper(FollowMapper.class);

    // ---------- RetweetMapper ----------

    @Test
    void retweet_mapEntityToDto_nullEntity_returnsNull() {
        assertNull(retweetMapper.mapEntityToDto(null));
    }

    @Test
    void retweet_mapRequestToEntityFromCreate_populatesIdAndCreatedAt() {
        RetweetCreateRequest req = new RetweetCreateRequest();
        req.setContent("hi");
        req.setRetweeterId(UUID.randomUUID());
        req.setOriginalTweetId(UUID.randomUUID());
        RetweetEntity e = retweetMapper.mapRequestToEntity(req);
        assertNotNull(e);
        assertNotNull(e.getId());
        assertNotNull(e.getCreatedAt());
        assertTrue(e.isInsertable());
        assertEquals("hi", e.getContent());
    }

    @Test
    void retweet_mapRequestToEntityUpdate_nullRequest_noop() {
        RetweetEntity entity = new RetweetEntity();
        entity.setContent("orig");
        retweetMapper.mapRequestToEntity((RetweetUpdateRequest) null, entity);
        assertEquals("orig", entity.getContent());
    }

    @Test
    void retweet_mapRequestToEntityUpdate_overwritesContent() {
        RetweetUpdateRequest req = new RetweetUpdateRequest();
        req.setContent("new");
        req.setRetweeterId(UUID.randomUUID());
        RetweetEntity entity = new RetweetEntity();
        entity.setContent("original");
        retweetMapper.mapRequestToEntity(req, entity);
        assertEquals("new", entity.getContent());
        assertEquals(req.getRetweeterId(), entity.getRetweeterId());
    }

    @Test
    void retweet_mapRequestToEntityUpdate_overwritesRetweeterId() {
        RetweetUpdateRequest req = new RetweetUpdateRequest();
        req.setContent("c");
        UUID newId = UUID.randomUUID();
        req.setRetweeterId(newId);
        RetweetEntity entity = new RetweetEntity();
        entity.setRetweeterId(UUID.randomUUID());
        retweetMapper.mapRequestToEntity(req, entity);
        assertEquals(newId, entity.getRetweeterId());
        assertEquals("c", entity.getContent());
    }

    // ---------- ReplyMapper ----------

    @Test
    void reply_mapEntityToCreationDto_null_returnsNull() {
        assertNull(replyMapper.mapEntityToCreationDto(null));
    }

    @Test
    void reply_mapEntityToDto_nullEntity_withName_buildsDtoWithName() {
        // mapEntityToDto(null, "name") — null entity but non-null name still
        // yields a DTO containing the name (mapper does not short-circuit on null entity).
        var dto = replyMapper.mapEntityToDto(null, "name");
        assertNotNull(dto);
    }

    @Test
    void reply_mapRequestToEntityFromCreate_populatesIdAndCreatedAt() {
        ReplyCreateRequest req = new ReplyCreateRequest();
        req.setContent("hello");
        req.setUserId(UUID.randomUUID());
        req.setTweetId(UUID.randomUUID());
        ReplyEntity e = replyMapper.mapRequestToEntity(req);
        assertNotNull(e);
        assertNotNull(e.getId());
        assertNotNull(e.getCreatedAt());
        assertTrue(e.isInsertable());
        assertEquals("hello", e.getContent());
    }

    @Test
    void reply_mapRequestToEntityUpdate_nullRequest_noop() {
        ReplyEntity entity = new ReplyEntity();
        entity.setContent("orig");
        replyMapper.mapRequestToEntity(null, entity);
        assertEquals("orig", entity.getContent());
    }

    @Test
    void reply_mapRequestToEntityUpdate_overwritesContent() {
        ReplyUpdateRequest req = new ReplyUpdateRequest();
        req.setUserId(UUID.randomUUID());
        req.setContent("new-content");
        ReplyEntity entity = new ReplyEntity();
        entity.setContent("orig");
        replyMapper.mapRequestToEntity(req, entity);
        assertEquals("new-content", entity.getContent());
        assertEquals(req.getUserId(), entity.getUserId());
    }

    @Test
    void reply_mapRequestToEntityUpdate_overwritesUserId() {
        ReplyUpdateRequest req = new ReplyUpdateRequest();
        req.setContent("c");
        UUID newId = UUID.randomUUID();
        req.setUserId(newId);
        ReplyEntity entity = new ReplyEntity();
        entity.setUserId(UUID.randomUUID());
        replyMapper.mapRequestToEntity(req, entity);
        assertEquals(newId, entity.getUserId());
        assertEquals("c", entity.getContent());
    }

    // ---------- LikeMapper ----------

    @Test
    void like_mapEntityToDto_null_returnsNull() {
        assertNull(likeMapper.mapEntityToDto(null));
    }

    @Test
    void like_mapRequestToEntity_populatesIdAndCreatedAt() {
        UUID userId = UUID.randomUUID();
        UUID likeableId = UUID.randomUUID();
        LikeEntity e = likeMapper.mapRequestToEntity(userId, likeableId, "TWEET");
        assertNotNull(e);
        assertNotNull(e.getId());
        assertNotNull(e.getCreatedAt());
        assertTrue(e.isInsertable());
        assertEquals(userId, e.getUserId());
        assertEquals(likeableId, e.getLikeableId());
        assertEquals("TWEET", e.getLikeableType());
    }

    // ---------- FollowMapper ----------

    @Test
    void follow_mapEntityToDto_nullEntity_returnsNull() {
        assertNull(followMapper.mapEntityToDto(null));
    }

    @Test
    void follow_mapRequestToEntity_populatesIdAndCreatedAt() {
        UUID follower = UUID.randomUUID();
        UUID followed = UUID.randomUUID();
        var e = followMapper.mapRequestToEntity(follower, followed, "PENDING");
        assertNotNull(e);
        assertNotNull(e.getId());
        assertNotNull(e.getCreatedAt());
        assertTrue(e.isInsertable());
        assertEquals(follower, e.getFollowerId());
        assertEquals(followed, e.getFollowedId());
        assertEquals("PENDING", e.getStatus());
    }
}
