/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.config;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import ro.tweebyte.interactionservice.model.TweetDto;
import ro.tweebyte.interactionservice.model.UserDto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves the per-cache serializers configured in {@link CacheConfiguration} round-trip
 * each cache's OWN value type — the regression that motivated replacing the single
 * UserDto-typed default. The critical case is {@code popular_users}
 * (Map&lt;UUID,Double&gt;): the UUID map keys must survive so
 * {@code RecommendationService.fetchPopularUsers()} can match them against a
 * {@code Set<UUID>}.
 */
class CacheSerializationRoundTripTests {

	private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

	/**
	 * Resolves the value-serialization pair that {@link CacheConfiguration} wires for
	 * {@code cacheName}, falling back to the default pair (used by every cache that has
	 * no explicit per-cache override). The pair's
	 * {@code write(Object)}/{@code read(ByteBuffer)} are exactly what Spring's cache
	 * machinery uses, so round-tripping through them proves the configured serializer
	 * handles the cache's real value type.
	 */
	@SuppressWarnings("unchecked")
	private RedisSerializationContext.SerializationPair<Object> pairForCache(String cacheName) throws Exception {
		CacheConfiguration config = new CacheConfiguration();
		// cacheManager's TTL is @Value-injected under Spring; called directly here
		// it needs an explicit Duration (the property value, 60s).
		RedisCacheManager manager = config.cacheManager(mock(RedisConnectionFactory.class), this.objectMapper,
				Duration.ofSeconds(60));

		Field initialField = RedisCacheManager.class.getDeclaredField("initialCacheConfiguration");
		initialField.setAccessible(true);
		Map<String, RedisCacheConfiguration> initial = (Map<String, RedisCacheConfiguration>) initialField.get(manager);

		Field defaultsField = RedisCacheManager.class.getDeclaredField("defaultCacheConfiguration");
		defaultsField.setAccessible(true);
		RedisCacheConfiguration defaults = (RedisCacheConfiguration) defaultsField.get(manager);

		RedisCacheConfiguration cacheConfig = initial.getOrDefault(cacheName, defaults);
		return (RedisSerializationContext.SerializationPair<Object>) cacheConfig.getValueSerializationPair();
	}

	private Object roundTrip(String cacheName, Object value) throws Exception {
		RedisSerializationContext.SerializationPair<Object> pair = pairForCache(cacheName);
		ByteBuffer buffer = pair.write(value);
		return pair.read(buffer);
	}

	@Test
	void usersCache_roundTripsUserDto() throws Exception {
		UserDto u = new UserDto(UUID.randomUUID(), "alice", false, null);

		Object back = roundTrip("users", u);

		assertThat(back).isInstanceOf(UserDto.class).isEqualTo(u);
	}

	@Test
	void tweetsCache_roundTripsTweetDto() throws Exception {
		TweetDto t = new TweetDto();
		t.setId(UUID.randomUUID());
		t.setContent("hello");

		Object back = roundTrip("tweets", t);

		assertThat(back).isInstanceOf(TweetDto.class).isEqualTo(t);
	}

	@Test
	void userTweetsCache_roundTripsListOfTweetDto() throws Exception {
		TweetDto t = new TweetDto();
		t.setId(UUID.randomUUID());
		List<TweetDto> list = List.of(t);

		Object back = roundTrip("user_tweets", list);

		assertThat(back).isInstanceOf(List.class).isEqualTo(list);
	}

	@Test
	void popularHashtagsCache_roundTripsListOfHashtagDto() throws Exception {
		TweetDto.HashtagDto h = new TweetDto.HashtagDto(UUID.randomUUID(), "#x", 3L);
		List<TweetDto.HashtagDto> list = List.of(h);

		Object back = roundTrip("popular_hashtags", list);

		assertThat(back).isInstanceOf(List.class).isEqualTo(list);
	}

	@Test
	void followRecommendationsCache_roundTripsListOfUserDto() throws Exception {
		UserDto u = new UserDto(UUID.randomUUID(), "bob", true, null);
		List<UserDto> list = List.of(u);

		Object back = roundTrip("follow_recommendations", list);

		assertThat(back).isInstanceOf(List.class).isEqualTo(list);
	}

	@Test
	void popularUsersCache_roundTripsMapWithUuidKeysIntact() throws Exception {
		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		Map<UUID, Double> map = new LinkedHashMap<>();
		map.put(id1, 9.0);
		map.put(id2, 4.0);

		Object back = roundTrip("popular_users", map);

		assertThat(back).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<Object, Object> result = (Map<Object, Object>) back;
		// The load-bearing assertion: keys must come back as UUID, not String,
		// so followedIds.contains(key) works against a Set<UUID>.
		assertThat(result.keySet().stream().allMatch(k -> k instanceof UUID))
			.as("popular_users map keys must deserialize back to UUID")
			.isTrue();
		assertThat(result).containsKey(id1).containsKey(id2);
		assertThat(((Number) result.get(id1)).doubleValue()).isEqualTo(9.0);
	}

}
