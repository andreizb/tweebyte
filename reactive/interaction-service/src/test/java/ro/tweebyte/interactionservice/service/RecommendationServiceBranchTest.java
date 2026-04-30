package ro.tweebyte.interactionservice.service;

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
import ro.tweebyte.interactionservice.model.Status;
import ro.tweebyte.interactionservice.model.UserDto;
import ro.tweebyte.interactionservice.repository.FollowRepository;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Branch coverage for RecommendationService popular-user filtering on
 * (.filter(popularId -> !followedIds.contains(popularId))) —
 * exercises both include and exclude outcomes by feeding populated
 * popular-users maps through the recommendation pipeline.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceBranchTest {

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
        ReflectionTestUtils.setField(recommendationService, "self", recommendationService);
        valueOps = mock(ReactiveValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void fetchUserRecommendations_PopularUserNotInFollowed_PassesFilter() throws JsonProcessingException {
        // Drives the popular-user filter:
        //   .filter(popularId -> !followedIds.contains(popularId))
        // We arrange a popular user whose id is NOT in the followed-set —
        // the predicate evaluates true, the user flows through to
        // userService.getUserSummary, and we observe the dto downstream.
        UUID userId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();      // user the caller already follows
        UUID popularUserId = UUID.randomUUID();   // popular and unrelated → must pass filter

        // Cache miss for the user-recommendations key, so fetchUserRecommendations
        // is invoked.
        String userRecKey = "follow_recommendations:" + userId;
        when(valueOps.get(userRecKey)).thenReturn(Mono.empty());
        when(valueOps.set(eq(userRecKey), anyString())).thenReturn(Mono.just(true));

        // Cache hit for popular-users so the popular-users branch resolves
        // synchronously with a known map.
        String popularJson = objectMapper.writeValueAsString(Map.of(popularUserId, 99.0));
        when(valueOps.get("popular_users:")).thenReturn(Mono.just(popularJson));
        // Defensive stub: switchIfEmpty's body is eagerly constructed
        // (computePopularUsers builds a pipeline off findAllFollowedIds())
        // even when the cache hit short-circuits before subscription.
        // Without this stub Mockito returns null and the chain NPEs at
        // construction time.
        lenient().when(followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

        // The caller follows `followedId`. That followed user follows nobody,
        // so the second-degree recommendations Flux is empty and the popular
        // users path is the only contributor.
        FollowEntity callerFollow = new FollowEntity();
        callerFollow.setFollowerId(userId);
        callerFollow.setFollowedId(followedId);
        when(followRepository.findByFollowerIdAndStatus(eq(userId), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(callerFollow));
        when(followRepository.findByFollowerIdAndStatus(eq(followedId), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.empty());

        UserDto popularDto = new UserDto();
        popularDto.setId(popularUserId);
        when(userService.getUserSummary(popularUserId)).thenReturn(Mono.just(popularDto));

        StepVerifier.create(recommendationService.recommendUsersToFollow(userId))
            .expectNextMatches(u -> popularUserId.equals(u.getId()))
            .verifyComplete();
    }

    @Test
    void fetchUserRecommendations_SecondDegreeFollow_PassesFilter() throws JsonProcessingException {
        // Drives the friend-of-a-friend filter with a candidate the caller does NOT already
        // follow and who is NOT the caller themselves — both sub-predicates
        // are true so the entity is recommended.
        UUID userId = UUID.randomUUID();
        UUID directlyFollowed = UUID.randomUUID();   // caller follows this user
        UUID secondDegree = UUID.randomUUID();        // friend-of-a-friend, not yet followed

        String userRecKey = "follow_recommendations:" + userId;
        when(valueOps.get(userRecKey)).thenReturn(Mono.empty());
        when(valueOps.set(eq(userRecKey), anyString())).thenReturn(Mono.just(true));
        when(valueOps.get("popular_users:")).thenReturn(Mono.just("{}"));
        lenient().when(followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

        // Caller's direct follows.
        FollowEntity callerFollow = new FollowEntity();
        callerFollow.setFollowerId(userId);
        callerFollow.setFollowedId(directlyFollowed);
        when(followRepository.findByFollowerIdAndStatus(eq(userId), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(callerFollow));

        // Direct-follow's own follows: a second-degree user the caller does
        // NOT already follow. This entity flows through the friend-of-a-friend filter and both
        // sub-predicates evaluate true.
        FollowEntity secondDegreeFollow = new FollowEntity();
        secondDegreeFollow.setFollowerId(directlyFollowed);
        secondDegreeFollow.setFollowedId(secondDegree);
        when(followRepository.findByFollowerIdAndStatus(eq(directlyFollowed), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(secondDegreeFollow));

        UserDto secondDegreeDto = new UserDto();
        secondDegreeDto.setId(secondDegree);
        when(userService.getUserSummary(secondDegree)).thenReturn(Mono.just(secondDegreeDto));

        StepVerifier.create(recommendationService.recommendUsersToFollow(userId))
            .expectNextMatches(u -> secondDegree.equals(u.getId()))
            .verifyComplete();
    }

    @Test
    void fetchUserRecommendations_SecondDegreeIsCallerSelf_FilteredOut() throws JsonProcessingException {
        // Drives the !userId.equals(entity.getFollowedId()) sub-predicate to
        // FALSE on the friend-of-a-friend filter: a friend-of-a-friend whose followed user is the
        // caller themselves must NOT be recommended (self-follow guard).
        UUID userId = UUID.randomUUID();
        UUID directlyFollowed = UUID.randomUUID();

        String userRecKey = "follow_recommendations:" + userId;
        when(valueOps.get(userRecKey)).thenReturn(Mono.empty());
        when(valueOps.set(eq(userRecKey), anyString())).thenReturn(Mono.just(true));
        when(valueOps.get("popular_users:")).thenReturn(Mono.just("{}"));
        lenient().when(followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

        FollowEntity callerFollow = new FollowEntity();
        callerFollow.setFollowerId(userId);
        callerFollow.setFollowedId(directlyFollowed);
        when(followRepository.findByFollowerIdAndStatus(eq(userId), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(callerFollow));

        // The directly-followed user follows the caller back. Without the
        // !userId.equals(...) guard the caller would be recommended their
        // own profile.
        FollowEntity reciprocal = new FollowEntity();
        reciprocal.setFollowerId(directlyFollowed);
        reciprocal.setFollowedId(userId);
        when(followRepository.findByFollowerIdAndStatus(eq(directlyFollowed), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(reciprocal));

        StepVerifier.create(recommendationService.recommendUsersToFollow(userId))
            .verifyComplete();
    }

    @Test
    void fetchUserRecommendations_SecondDegreeAlreadyFollowed_FilteredOut() throws JsonProcessingException {
        // Drives the !followedIds.contains(...) sub-predicate to FALSE on
        // friend-of-a-friend filter: a friend-of-a-friend who is already directly followed by the
        // caller must NOT appear as a recommendation.
        UUID userId = UUID.randomUUID();
        UUID directlyFollowedA = UUID.randomUUID();
        UUID directlyFollowedB = UUID.randomUUID();   // also already followed

        String userRecKey = "follow_recommendations:" + userId;
        when(valueOps.get(userRecKey)).thenReturn(Mono.empty());
        when(valueOps.set(eq(userRecKey), anyString())).thenReturn(Mono.just(true));
        when(valueOps.get("popular_users:")).thenReturn(Mono.just("{}"));
        lenient().when(followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

        // Caller follows both A and B.
        FollowEntity followA = new FollowEntity();
        followA.setFollowerId(userId);
        followA.setFollowedId(directlyFollowedA);
        FollowEntity followB = new FollowEntity();
        followB.setFollowerId(userId);
        followB.setFollowedId(directlyFollowedB);
        when(followRepository.findByFollowerIdAndStatus(eq(userId), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(followA, followB));

        // A also follows B. B is already followed by the caller, so the
        // contains-guard rejects it.
        FollowEntity aFollowsB = new FollowEntity();
        aFollowsB.setFollowerId(directlyFollowedA);
        aFollowsB.setFollowedId(directlyFollowedB);
        when(followRepository.findByFollowerIdAndStatus(eq(directlyFollowedA), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(aFollowsB));
        when(followRepository.findByFollowerIdAndStatus(eq(directlyFollowedB), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.empty());

        StepVerifier.create(recommendationService.recommendUsersToFollow(userId))
            .verifyComplete();
    }

    @Test
    void fetchUserRecommendations_PopularUserAlreadyFollowed_FilteredOut() throws JsonProcessingException {
        // Covers the other side of the popular-user filter: a popular user the
        // caller already follows must NOT appear in the recommendation
        // stream — exercises the "false" outcome of the predicate.
        UUID userId = UUID.randomUUID();
        UUID followedId = UUID.randomUUID();

        String userRecKey = "follow_recommendations:" + userId;
        when(valueOps.get(userRecKey)).thenReturn(Mono.empty());
        when(valueOps.set(eq(userRecKey), anyString())).thenReturn(Mono.just(true));

        // Popular user IS followedId — must be filtered out by the popular-user filter.
        String popularJson = objectMapper.writeValueAsString(Map.of(followedId, 50.0));
        when(valueOps.get("popular_users:")).thenReturn(Mono.just(popularJson));
        lenient().when(followRepository.findAllFollowedIds()).thenReturn(Flux.empty());

        FollowEntity callerFollow = new FollowEntity();
        callerFollow.setFollowerId(userId);
        callerFollow.setFollowedId(followedId);
        when(followRepository.findByFollowerIdAndStatus(eq(userId), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.just(callerFollow));
        when(followRepository.findByFollowerIdAndStatus(eq(followedId), eq(Status.ACCEPTED.name())))
            .thenReturn(Flux.empty());

        // userService.getUserSummary should NOT be called — recommendation
        // stream is empty.
        StepVerifier.create(recommendationService.recommendUsersToFollow(userId))
            .verifyComplete();
    }
}
