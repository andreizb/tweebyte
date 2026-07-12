/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.mapper;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.model.FollowDto;
import ro.tweebyte.interactionservice.model.ReplyDto;
import ro.tweebyte.interactionservice.model.ReplyUpdateRequest;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Branch-coverage tests for the four mappers — exercises the null-input guard arms that
 * the existing happy-path mapper tests skip.
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
		assertThat(this.retweetMapper.mapEntityToDto(null, new UserDto())).isNull();
		assertThat(this.retweetMapper.mapEntityToDto(null, new UserDto(), new TweetDto())).isNull();
	}

	@Test
	void retweet_mapRequestToEntity_nullRequest_noop() {
		RetweetEntity entity = new RetweetEntity();
		entity.setContent("orig");
		this.retweetMapper.mapRequestToEntity((RetweetUpdateRequest) null, entity);
		assertThat(entity.getContent()).isEqualTo("orig");
	}

	@Test
	void retweet_mapRequestToEntity_nullEntity_noop() {
		RetweetUpdateRequest req = new RetweetUpdateRequest();
		req.setContent("new");
		// Null target entity must be tolerated without throwing.
		assertThatCode(() -> this.retweetMapper.mapRequestToEntity(req, null)).doesNotThrowAnyException();
	}

	@Test
	void retweet_mapRequestToEntity_nullContentLeavesEntity() {
		RetweetUpdateRequest req = new RetweetUpdateRequest();
		// content stays null
		req.setRetweeterId(UUID.randomUUID());
		RetweetEntity entity = new RetweetEntity();
		entity.setContent("original");
		this.retweetMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getContent()).isEqualTo("original");
		assertThat(entity.getRetweeterId()).isEqualTo(req.getRetweeterId());
	}

	@Test
	void retweet_mapRequestToEntity_nullRetweeterIdLeavesEntity() {
		RetweetUpdateRequest req = new RetweetUpdateRequest();
		req.setContent("c");
		// retweeterId stays null
		UUID original = UUID.randomUUID();
		RetweetEntity entity = new RetweetEntity();
		entity.setRetweeterId(original);
		this.retweetMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getRetweeterId()).isEqualTo(original);
		assertThat(entity.getContent()).isEqualTo("c");
	}

	// ---------- ReplyMapper ----------

	@Test
	void reply_mapEntityToCreationDto_null_returnsNull() {
		assertThat(this.replyMapper.mapEntityToCreationDto(null)).isNull();
	}

	@Test
	void reply_mapEntityToDto_nullEntity_withName_buildsDtoWithName() {
		// null entity but non-null name still yields a DTO containing the name
		// (mapper does not short-circuit on null entity).
		ReplyDto dto = this.replyMapper.mapEntityToDto(null, "name");
		assertThat(dto).isNotNull();
		assertThat(dto.getUserName()).isEqualTo("name");
	}

	@Test
	void reply_mapCreationRequestToEntity_null_returnsNull() {
		assertThat(this.replyMapper.mapCreationRequestToEntity(null)).isNull();
	}

	@Test
	void reply_mapRequestToEntity_nullRequest_noop() {
		ReplyEntity entity = new ReplyEntity();
		entity.setContent("orig");
		this.replyMapper.mapRequestToEntity(null, entity);
		assertThat(entity.getContent()).isEqualTo("orig");
	}

	@Test
	void reply_mapRequestToEntity_nullEntity_noop() {
		ReplyUpdateRequest req = new ReplyUpdateRequest();
		assertThatCode(() -> this.replyMapper.mapRequestToEntity(req, null)).doesNotThrowAnyException();
	}

	@Test
	void reply_mapRequestToEntity_nullContentLeavesEntity() {
		ReplyUpdateRequest req = new ReplyUpdateRequest();
		req.setUserId(UUID.randomUUID());
		ReplyEntity entity = new ReplyEntity();
		entity.setContent("orig");
		this.replyMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getContent()).isEqualTo("orig");
		assertThat(entity.getUserId()).isEqualTo(req.getUserId());
	}

	@Test
	void reply_mapRequestToEntity_nullUserIdLeavesEntity() {
		ReplyUpdateRequest req = new ReplyUpdateRequest();
		req.setContent("c");
		UUID original = UUID.randomUUID();
		ReplyEntity entity = new ReplyEntity();
		entity.setUserId(original);
		this.replyMapper.mapRequestToEntity(req, entity);
		assertThat(entity.getUserId()).isEqualTo(original);
		assertThat(entity.getContent()).isEqualTo("c");
	}

	// ---------- LikeMapper ----------

	@Test
	void like_mapEntityToDto_null_returnsNull() {
		assertThat(this.likeMapper.mapEntityToDto(null)).isNull();
	}

	@Test
	void like_mapToDto_nullEntity_returnsNull() {
		assertThat(this.likeMapper.mapToDto(null, new UserDto())).isNull();
		assertThat(this.likeMapper.mapToDto(null, new TweetDto())).isNull();
	}

	@Test
	void like_mapCreationRequestToEntity_allNull_returnsNull() {
		assertThat(this.likeMapper.mapCreationRequestToEntity(null, null, null)).isNull();
	}

	@Test
	void like_mapCreationRequestToEntity_someNull_returnsEntity() {
		// Negative branch of the and-chain: at least one non-null → builds entity.
		LikeEntity e = this.likeMapper.mapCreationRequestToEntity(UUID.randomUUID(), null, null);
		assertThat(e).isNotNull();
	}

	// ---------- FollowMapper ----------

	@Test
	void follow_mapEntityToDto_nullEntity_returnsNull() {
		assertThat(this.followMapper.mapEntityToDto(null)).isNull();
	}

	@Test
	void follow_mapEntityToDto_nullEntity_nullName_returnsNull() {
		assertThat(this.followMapper.mapEntityToDto(null, null)).isNull();
	}

	@Test
	void follow_mapEntityToDto_nullEntity_butWithName_returnsDto() {
		// mapEntityToDto(null, "x") — null entity but non-null name builds an empty DTO.
		FollowDto dto = this.followMapper.mapEntityToDto(null, "name");
		assertThat(dto).isNotNull();
		assertThat(dto.getUserName()).isEqualTo("name");
	}

	@Test
	void follow_mapEntityToDto_nullStatus_dtoStatusNull() {
		FollowEntity e = new FollowEntity();
		e.setStatus(null);
		FollowDto dto = this.followMapper.mapEntityToDto(e);
		assertThat(dto).isNotNull();
		assertThat(dto.getStatus()).isNull();
	}

	@Test
	void follow_mapEntityToDto_withName_setsName() {
		FollowEntity e = new FollowEntity();
		e.setStatus(FollowEntity.Status.ACCEPTED);
		FollowDto dto = this.followMapper.mapEntityToDto(e, "alice");
		assertThat(dto).isNotNull();
		assertThat(dto.getUserName()).isEqualTo("alice");
		assertThat(dto.getStatus()).isEqualTo(FollowDto.Status.ACCEPTED);
	}

}
