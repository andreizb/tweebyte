/*
 * Copyright 2026 Tweebyte contributors
 * SPDX-License-Identifier: MIT
 */

package ro.tweebyte.interactionservice.service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import ro.tweebyte.interactionservice.entity.FollowEntity;
import ro.tweebyte.interactionservice.exception.InteractionException;
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

/**
 * Branch coverage for RecommendationService popular-user filtering on (.filter(popularId
 * -> !followedIds.contains(popularId))) — exercises both include and exclude outcomes by
 * feeding populated popular-users maps through the recommendation pipeline.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceBranchTests {

	@InjectMocks
	private RecommendationService recommendationService;

	@Mock
	private UserService userService;

	@Mock
	private TweetService tweetService;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private LikeService likeService;

	@Mock
	private RetweetService retweetService;

	@Mock
	private ReactiveRedisTemplate<String, String> redisTemplate;

	private ReactiveValueOperations<String, String> valueOps;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	@SuppressWarnings("unchecked")
	void init() {
		ReflectionTestUtils.setField(this.recommendationService, "self", this.recommendationService);
		// @Value-injected TTL is null without a Spring context; set it so the
		// cache-miss set(key, json, cacheTtl) write passes a real Duration
		// (Mockito's any(Duration.class) won't match null).
		ReflectionTestUtils.setField(this.recommendationService, "cacheTtl", Duration.ofSeconds(60));
		this.valueOps = mock(ReactiveValueOperations.class);
		lenient().when(this.redisTemplate.opsForValue()).thenReturn(this.valueOps);
	}

	@Test
	void fetchUserRecommendations_PopularUserNotInFollowed_PassesFilter() throws JsonProcessingException {
		// Drives the popular-user filter:
		// .filter(popularId -> !followedIds.contains(popularId))
		// We arrange a popular user whose id is NOT in the followed-set —
		// the predicate evaluates true, the user flows through to
		// userService.getUserSummary, and we observe the dto downstream.
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID(); // user the caller already follows
		UUID popularUserId = UUID.randomUUID(); // popular and unrelated → must pass
												// filter

		// Cache miss for the user-recommendations key, so fetchUserRecommendations
		// is invoked.
		String userRecKey = "follow_recommendations::" + userId;
		given(this.valueOps.get(userRecKey)).willReturn(Mono.empty());
		given(this.valueOps.set(eq(userRecKey), anyString(), any(Duration.class))).willReturn(Mono.just(true));

		// Cache hit for popular-users so the popular-users branch resolves
		// synchronously with a known map.
		String popularJson = this.objectMapper.writeValueAsString(Map.of(popularUserId, 99.0));
		given(this.valueOps.get("popular_users::")).willReturn(Mono.just(popularJson));
		// Defensive stub: switchIfEmpty's body is eagerly constructed
		// (computePopularUsers builds a pipeline off findAllFollowedIds())
		// even when the cache hit short-circuits before subscription.
		// Without this stub Mockito returns null and the chain NPEs at
		// construction time.
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		// The caller follows `followedId`. That followed user follows nobody,
		// so the second-degree recommendations Flux is empty and the popular
		// users path is the only contributor.
		FollowEntity callerFollow = new FollowEntity();
		callerFollow.setFollowerId(userId);
		callerFollow.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Flux.just(callerFollow));
		given(this.followRepository.findByFollowerIdInAndStatus(Set.of(followedId), Status.ACCEPTED.name()))
			.willReturn(Flux.empty());

		UserDto popularDto = new UserDto();
		popularDto.setId(popularUserId);
		given(this.userService.getUserSummary(popularUserId)).willReturn(Mono.just(popularDto));

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId))
			.expectNextMatches(u -> popularUserId.equals(u.getId()))
			.verifyComplete();
	}

	@Test
	void fetchUserRecommendations_SecondDegreeFollow_PassesFilter() {
		// Drives the friend-of-a-friend filter with a candidate the caller does NOT
		// already
		// follow and who is NOT the caller themselves — both sub-predicates
		// are true so the entity is recommended.
		UUID userId = UUID.randomUUID();
		UUID directlyFollowed = UUID.randomUUID(); // caller follows this user
		UUID secondDegree = UUID.randomUUID(); // friend-of-a-friend, not yet followed

		String userRecKey = "follow_recommendations::" + userId;
		given(this.valueOps.get(userRecKey)).willReturn(Mono.empty());
		given(this.valueOps.set(eq(userRecKey), anyString(), any(Duration.class))).willReturn(Mono.just(true));
		given(this.valueOps.get("popular_users::")).willReturn(Mono.just("{}"));
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		// Caller's direct follows.
		FollowEntity callerFollow = new FollowEntity();
		callerFollow.setFollowerId(userId);
		callerFollow.setFollowedId(directlyFollowed);
		given(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Flux.just(callerFollow));

		// Direct-follow's own follows: a second-degree user the caller does
		// NOT already follow. This entity flows through the friend-of-a-friend filter and
		// both
		// sub-predicates evaluate true.
		FollowEntity secondDegreeFollow = new FollowEntity();
		secondDegreeFollow.setFollowerId(directlyFollowed);
		secondDegreeFollow.setFollowedId(secondDegree);
		given(this.followRepository.findByFollowerIdInAndStatus(Set.of(directlyFollowed), Status.ACCEPTED.name()))
			.willReturn(Flux.just(secondDegreeFollow));

		UserDto secondDegreeDto = new UserDto();
		secondDegreeDto.setId(secondDegree);
		given(this.userService.getUserSummary(secondDegree)).willReturn(Mono.just(secondDegreeDto));

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId))
			.expectNextMatches(u -> secondDegree.equals(u.getId()))
			.verifyComplete();
	}

	@Test
	void fetchUserRecommendations_SecondDegreeIsCallerSelf_FilteredOut() {
		// Drives the !userId.equals(entity.getFollowedId()) sub-predicate to
		// FALSE on the friend-of-a-friend filter: a friend-of-a-friend whose followed
		// user is the
		// caller themselves must NOT be recommended (self-follow guard).
		UUID userId = UUID.randomUUID();
		UUID directlyFollowed = UUID.randomUUID();

		String userRecKey = "follow_recommendations::" + userId;
		given(this.valueOps.get(userRecKey)).willReturn(Mono.empty());
		given(this.valueOps.set(eq(userRecKey), anyString(), any(Duration.class))).willReturn(Mono.just(true));
		given(this.valueOps.get("popular_users::")).willReturn(Mono.just("{}"));
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		FollowEntity callerFollow = new FollowEntity();
		callerFollow.setFollowerId(userId);
		callerFollow.setFollowedId(directlyFollowed);
		given(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Flux.just(callerFollow));

		// The directly-followed user follows the caller back. Without the
		// !userId.equals(...) guard the caller would be recommended their
		// own profile.
		FollowEntity reciprocal = new FollowEntity();
		reciprocal.setFollowerId(directlyFollowed);
		reciprocal.setFollowedId(userId);
		given(this.followRepository.findByFollowerIdInAndStatus(Set.of(directlyFollowed), Status.ACCEPTED.name()))
			.willReturn(Flux.just(reciprocal));

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId)).verifyComplete();
	}

	@Test
	void fetchUserRecommendations_SecondDegreeAlreadyFollowed_FilteredOut() {
		// Drives the !followedIds.contains(...) sub-predicate to FALSE on
		// friend-of-a-friend filter: a friend-of-a-friend who is already directly
		// followed by the
		// caller must NOT appear as a recommendation.
		UUID userId = UUID.randomUUID();
		UUID directlyFollowedA = UUID.randomUUID();
		UUID directlyFollowedB = UUID.randomUUID(); // also already followed

		String userRecKey = "follow_recommendations::" + userId;
		given(this.valueOps.get(userRecKey)).willReturn(Mono.empty());
		given(this.valueOps.set(eq(userRecKey), anyString(), any(Duration.class))).willReturn(Mono.just(true));
		given(this.valueOps.get("popular_users::")).willReturn(Mono.just("{}"));
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		// Caller follows both A and B.
		FollowEntity followA = new FollowEntity();
		followA.setFollowerId(userId);
		followA.setFollowedId(directlyFollowedA);
		FollowEntity followB = new FollowEntity();
		followB.setFollowerId(userId);
		followB.setFollowedId(directlyFollowedB);
		given(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Flux.just(followA, followB));

		// A also follows B. B is already followed by the caller, so the
		// contains-guard rejects it.
		FollowEntity aFollowsB = new FollowEntity();
		aFollowsB.setFollowerId(directlyFollowedA);
		aFollowsB.setFollowedId(directlyFollowedB);
		given(this.followRepository.findByFollowerIdInAndStatus(Set.of(directlyFollowedA, directlyFollowedB),
				Status.ACCEPTED.name()))
			.willReturn(Flux.just(aFollowsB));

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId)).verifyComplete();
	}

	@Test
	void fetchUserRecommendations_PopularUserAlreadyFollowed_FilteredOut() throws JsonProcessingException {
		// Covers the other side of the popular-user filter: a popular user the
		// caller already follows must NOT appear in the recommendation
		// stream — exercises the "false" outcome of the predicate.
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();

		String userRecKey = "follow_recommendations::" + userId;
		given(this.valueOps.get(userRecKey)).willReturn(Mono.empty());
		given(this.valueOps.set(eq(userRecKey), anyString(), any(Duration.class))).willReturn(Mono.just(true));

		// Popular user IS followedId — must be filtered out by the popular-user filter.
		String popularJson = this.objectMapper.writeValueAsString(Map.of(followedId, 50.0));
		given(this.valueOps.get("popular_users::")).willReturn(Mono.just(popularJson));
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		FollowEntity callerFollow = new FollowEntity();
		callerFollow.setFollowerId(userId);
		callerFollow.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Flux.just(callerFollow));
		given(this.followRepository.findByFollowerIdInAndStatus(Set.of(followedId), Status.ACCEPTED.name()))
			.willReturn(Flux.empty());

		// userService.getUserSummary should NOT be called — recommendation
		// stream is empty.
		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId)).verifyComplete();
	}

	@Test
	void fetchUserRecommendations_PopularUserIsCallerSelf_FilteredOut() throws JsonProcessingException {
		// Drives the popular-user filter's !userId.equals(popularId) sub-predicate to FALSE:
		// the viewer is themselves "popular" (followed by others), so they must not be
		// recommended their own profile. The recommendation stream is empty.
		UUID userId = UUID.randomUUID();
		UUID followedId = UUID.randomUUID();

		String userRecKey = "follow_recommendations::" + userId;
		given(this.valueOps.get(userRecKey)).willReturn(Mono.empty());
		given(this.valueOps.set(eq(userRecKey), anyString(), any(Duration.class))).willReturn(Mono.just(true));

		// The viewer's own id is in the popular set — must be filtered out by the self guard.
		String popularJson = this.objectMapper.writeValueAsString(Map.of(userId, 50.0));
		given(this.valueOps.get("popular_users::")).willReturn(Mono.just(popularJson));
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		FollowEntity callerFollow = new FollowEntity();
		callerFollow.setFollowerId(userId);
		callerFollow.setFollowedId(followedId);
		given(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Flux.just(callerFollow));
		given(this.followRepository.findByFollowerIdInAndStatus(Set.of(followedId), Status.ACCEPTED.name()))
			.willReturn(Flux.empty());

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId)).verifyComplete();
	}

	@Test
	void getUserRecommendations_CachedJsonMalformed_WrapsAsInteractionException() {
		// A corrupt cached recommendations payload fails readValue → the JsonProcessingException
		// catch arm wraps it as InteractionException rather than leaking the raw parse error. The
		// switchIfEmpty fetch body is assembled eagerly, so its repository reads are stubbed
		// defensively even though the parse error short-circuits before they are subscribed.
		UUID userId = UUID.randomUUID();
		given(this.valueOps.get("follow_recommendations::" + userId)).willReturn(Mono.just("not-json"));
		lenient().when(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.thenReturn(Flux.empty());
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId))
			.expectError(InteractionException.class)
			.verify();
	}

	@Test
	void fetchPopularUsers_CachedJsonMalformed_WrapsAsInteractionException() {
		// A corrupt cached popular-users payload fails readValue → the JsonProcessingException
		// catch arm in fetchPopularUsers wraps it as InteractionException. The single-flight body
		// is assembled eagerly, so findAllFollowedIds is stubbed even though it is never subscribed.
		given(this.valueOps.get("popular_users::")).willReturn(Mono.just("not-json"));
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

		StepVerifier.create(this.recommendationService.fetchPopularUsers())
			.expectError(InteractionException.class)
			.verify();
	}

	@Test
	void getUserRecommendations_WriteBackSerializationFails_WrapsAsInteractionException() {
		// Cache miss → fetch resolves one recommendation → the write-back serialises the list,
		// which throws → the doOnNext JsonProcessingException catch wraps it as InteractionException.
		installThrowingMapper();
		UUID userId = UUID.randomUUID();
		UUID popularUserId = UUID.randomUUID();
		given(this.valueOps.get("follow_recommendations::" + userId)).willReturn(Mono.empty());
		// Popular-users cache hit returns a single id so the fetch produces a non-empty list whose
		// serialisation is then attempted (and fails) on the write-back.
		given(this.valueOps.get("popular_users::"))
			.willReturn(Mono.just("{\"" + popularUserId + "\":99.0}"));
		lenient().when(this.followRepository.findAllFollowedIds()).thenReturn(Flux.empty());
		given(this.followRepository.findByFollowerIdAndStatus(userId, Status.ACCEPTED.name()))
			.willReturn(Flux.empty());
		UserDto popularDto = new UserDto();
		popularDto.setId(popularUserId);
		given(this.userService.getUserSummary(popularUserId)).willReturn(Mono.just(popularDto));

		StepVerifier.create(this.recommendationService.recommendUsersToFollow(userId))
			.expectError(InteractionException.class)
			.verify();
	}

	@Test
	void fetchPopularUsers_WriteBackSerializationFails_WrapsAsInteractionException() {
		// Cache miss → the single-flight computes the (empty) popular-users map → the write-back
		// serialises it, which throws → the doOnNext catch wraps it as InteractionException.
		installThrowingMapper();
		given(this.valueOps.get("popular_users::")).willReturn(Mono.empty());
		given(this.followRepository.findAllFollowedIds()).willReturn(Flux.empty());

		StepVerifier.create(this.recommendationService.fetchPopularUsers())
			.expectError(InteractionException.class)
			.verify();
	}

	// Swap in an ObjectMapper whose writeValueAsString always throws, reaching the write-back catch
	// arms. JsonProcessingException's constructor is protected, hence the anonymous subclass.
	private void installThrowingMapper() {
		try {
			JsonProcessingException failure = new JsonProcessingException("boom") {
			};
			ObjectMapper throwingMapper = spy(new ObjectMapper().findAndRegisterModules());
			willThrow(failure).given(throwingMapper).writeValueAsString(any());
			ReflectionTestUtils.setField(this.recommendationService, "objectMapper", throwingMapper);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
