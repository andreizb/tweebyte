/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.ReplyCreateRequest;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Branch-coverage tests for the four MapStruct mappers — exercises null-input guards and
 * partial-update arms that the existing happy-path mapper tests skip.
 */
class MapperBranchTests {

	private final RetweetMapper retweetMapper = Mappers.getMapper(RetweetMapper.class);

	private final ReplyMapper replyMapper = Mappers.getMapper(ReplyMapper.class);

	private final LikeMapper likeMapper = Mappers.getMapper(LikeMapper.class);

	private final FollowMapper followMapper = Mappers.getMapper(FollowMapper.class);

	// ---------- RetweetMapper ----------

	@Test
	void retweet_mapEntityToDto_nullEntity_returnsNull() {
		assertThat(this.retweetMapper.mapEntityToDto(null)).isNull();
	}

	@Test
	void retweet_mapRequestToEntityFromCreate_populatesIdAndCreatedAt() {
		RetweetCreateRequest req = new RetweetCreateRequest();
		req.setContent("hi");
		req.setRetweeterId(UUID.randomUUID());
		req.setOriginalTweetId(UUID.randomUUID());
		RetweetEntity e = this.retweetMapper.mapRequestToEntity(req);
		assertThat(e).isNotNull();
		assertThat(e.getId()).isNotNull();
		assertThat(e.getCreatedAt()).isNotNull();
		assertThat(e.isInsertable()).isTrue();
		assertThat(e.getContent()).isEqualTo("hi");
	}

	@Test
	void retweet_mapRequestToEntityUpdate_nullRequest_noop() {
		RetweetEntity entity = new RetweetEntity();
		entity.setContent("orig");
		this.retweetMapper.mapRequestToEntity((RetweetUpdateRequest) null, entity);
		assertThat(entity.getContent()).isEqualTo("orig");
	}

	@Test
	void retweet_mapRequestToEntityUpdate_overwritesContent() {
		RetweetUpdateRequest req = new RetweetUpdateRequest();
		req.setContent("new");
		req.setRetweeterId(UUID.randomUUID());
		RetweetEntity entity = new RetweetEntity();
		entity.setContent("original");
		this.retweetMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getContent()).isEqualTo("new");
		assertThat(entity.getRetweeterId()).isEqualTo(req.getRetweeterId());
	}

	@Test
	void retweet_mapRequestToEntityUpdate_overwritesRetweeterId() {
		RetweetUpdateRequest req = new RetweetUpdateRequest();
		req.setContent("c");
		UUID newId = UUID.randomUUID();
		req.setRetweeterId(newId);
		RetweetEntity entity = new RetweetEntity();
		entity.setRetweeterId(UUID.randomUUID());
		this.retweetMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getRetweeterId()).isEqualTo(newId);
		assertThat(entity.getContent()).isEqualTo("c");
	}

	// ---------- ReplyMapper ----------

	@Test
	void reply_mapEntityToCreationDto_null_returnsNull() {
		assertThat(this.replyMapper.mapEntityToCreationDto(null)).isNull();
	}

	@Test
	void reply_mapEntityToDto_nullEntity_withName_buildsDtoWithName() {
		// mapEntityToDto(null, "name") — null entity but non-null name still
		// yields a DTO containing the name (mapper does not short-circuit on null
		// entity).
		var dto = this.replyMapper.mapEntityToDto(null, "name");
		assertThat(dto).isNotNull();
	}

	@Test
	void reply_mapRequestToEntityFromCreate_populatesIdAndCreatedAt() {
		ReplyCreateRequest req = new ReplyCreateRequest();
		req.setContent("hello");
		req.setUserId(UUID.randomUUID());
		req.setTweetId(UUID.randomUUID());
		ReplyEntity e = this.replyMapper.mapRequestToEntity(req);
		assertThat(e).isNotNull();
		assertThat(e.getId()).isNotNull();
		assertThat(e.getCreatedAt()).isNotNull();
		assertThat(e.isInsertable()).isTrue();
		assertThat(e.getContent()).isEqualTo("hello");
	}

	@Test
	void reply_mapRequestToEntityUpdate_nullRequest_noop() {
		ReplyEntity entity = new ReplyEntity();
		entity.setContent("orig");
		this.replyMapper.mapRequestToEntity(null, entity);
		assertThat(entity.getContent()).isEqualTo("orig");
	}

	@Test
	void reply_mapRequestToEntityUpdate_overwritesContent() {
		ReplyUpdateRequest req = new ReplyUpdateRequest();
		req.setUserId(UUID.randomUUID());
		req.setContent("new-content");
		ReplyEntity entity = new ReplyEntity();
		entity.setContent("orig");
		this.replyMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getContent()).isEqualTo("new-content");
		assertThat(entity.getUserId()).isEqualTo(req.getUserId());
	}

	@Test
	void reply_mapRequestToEntityUpdate_overwritesUserId() {
		ReplyUpdateRequest req = new ReplyUpdateRequest();
		req.setContent("c");
		UUID newId = UUID.randomUUID();
		req.setUserId(newId);
		ReplyEntity entity = new ReplyEntity();
		entity.setUserId(UUID.randomUUID());
		this.replyMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getUserId()).isEqualTo(newId);
		assertThat(entity.getContent()).isEqualTo("c");
	}

	// ---------- LikeMapper ----------

	@Test
	void like_mapEntityToDto_null_returnsNull() {
		assertThat(this.likeMapper.mapEntityToDto(null)).isNull();
	}

	@Test
	void like_mapRequestToEntity_populatesIdAndCreatedAt() {
		UUID userId = UUID.randomUUID();
		UUID likeableId = UUID.randomUUID();
		LikeEntity e = this.likeMapper.mapRequestToEntity(userId, likeableId, "TWEET");
		assertThat(e).isNotNull();
		assertThat(e.getId()).isNotNull();
		assertThat(e.getCreatedAt()).isNotNull();
		assertThat(e.isInsertable()).isTrue();
		assertThat(e.getUserId()).isEqualTo(userId);
		assertThat(e.getLikeableId()).isEqualTo(likeableId);
		assertThat(e.getLikeableType()).isEqualTo("TWEET");
	}

	// ---------- FollowMapper ----------

	@Test
	void follow_mapEntityToDto_nullEntity_returnsNull() {
		assertThat(this.followMapper.mapEntityToDto(null)).isNull();
	}

	@Test
	void follow_mapRequestToEntity_populatesIdAndCreatedAt() {
		UUID follower = UUID.randomUUID();
		UUID followed = UUID.randomUUID();
		var e = this.followMapper.mapRequestToEntity(follower, followed, "PENDING");
		assertThat(e).isNotNull();
		assertThat(e.getId()).isNotNull();
		assertThat(e.getCreatedAt()).isNotNull();
		assertThat(e.isInsertable()).isTrue();
		assertThat(e.getFollowerId()).isEqualTo(follower);
		assertThat(e.getFollowedId()).isEqualTo(followed);
		assertThat(e.getStatus()).isEqualTo("PENDING");
	}

}
