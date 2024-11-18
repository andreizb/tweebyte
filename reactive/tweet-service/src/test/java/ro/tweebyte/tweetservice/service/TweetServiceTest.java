package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.tweetservice.client.InteractionClient;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetException;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.TweetMapper;
import ro.tweebyte.tweetservice.model.*;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TweetServiceTest {

	@InjectMocks
	private TweetService tweetService;

	@Mock
	private TweetRepository tweetRepository;

	@Mock
	private TweetMapper tweetMapper;

	@Mock
	private UserService userService;

	@Mock
	private MentionService mentionService;

	@Mock
	private HashtagService hashtagService;

	@Mock
	private InteractionClient interactionClient;

	@Mock
	private HashtagRepository hashtagRepository;

	@Mock
	private MentionRepository mentionRepository;

	@Mock
	private TweetHashtagRepository tweetHashtagRepository;

	@Mock
	private ReactiveRedisTemplate<String, String> redisTemplate;

	@Mock
	private ReactiveValueOperations<String, String> reactiveValueOperations;

	private UUID tweetId;
	private UUID userId;
	private TweetEntity tweetEntity;
	private TweetDto tweetDto;

	@BeforeEach
	public void setup() {
		tweetId = UUID.randomUUID();
		userId = UUID.randomUUID();

		tweetEntity = new TweetEntity();
		tweetEntity.setId(tweetId);
		tweetEntity.setUserId(userId);
		tweetEntity.setContent("Sample Tweet Content");
		tweetEntity.setCreatedAt(LocalDateTime.now());

		tweetDto = new TweetDto();
		tweetDto.setId(tweetId);
		tweetDto.setContent("Sample Tweet Content");
		tweetDto.setLikesCount(10L);
		tweetDto.setRepliesCount(5L);
		tweetDto.setRetweetsCount(3L);
	}

	@Test
	public void getUserFeed_Success() {
		UUID followedUserId = UUID.randomUUID();

		HashtagEntity hashtag = new HashtagEntity();
		hashtag.setId(UUID.randomUUID());
		hashtag.setText("#example");

		MentionEntity mention = new MentionEntity();
		mention.setId(UUID.randomUUID());
		mention.setUserId(followedUserId);

		ReplyDto reply = new ReplyDto();
		reply.setId(UUID.randomUUID());
		reply.setContent("Sample Reply");

		when(interactionClient.getFollowedIds(userId, "auth")).thenReturn(Flux.just(followedUserId));
		when(interactionClient.getLikesCount(tweetId, "auth")).thenReturn(Mono.just(10L));
		when(interactionClient.getRepliesCount(tweetId, "auth")).thenReturn(Mono.just(5L));
		when(interactionClient.getRetweetsCount(tweetId, "auth")).thenReturn(Mono.just(3L));
		when(interactionClient.getTopReply(tweetId, "auth")).thenReturn(Mono.just(reply));

		when(tweetRepository.findByUserIdInOrderByCreatedAtDesc(List.of(followedUserId)))
			.thenReturn(Flux.just(tweetEntity));
		when(hashtagRepository.findHashtagsByTweetId(tweetId)).thenReturn(Flux.just(hashtag));
		when(mentionRepository.findMentionsByTweetId(tweetId)).thenReturn(Flux.just(mention));

		when(tweetMapper.mapEntityToDto(any(), any(), any(), any(), any(ReplyDto.class), any(), any())).thenReturn(tweetDto);

		StepVerifier.create(tweetService.getUserFeed(userId, "auth"))
			.expectNext(tweetDto)
			.verifyComplete();

		verify(interactionClient).getFollowedIds(userId, "auth");
		verify(interactionClient).getLikesCount(tweetId, "auth");
		verify(interactionClient).getRepliesCount(tweetId, "auth");
		verify(interactionClient).getRetweetsCount(tweetId, "auth");
		verify(interactionClient).getTopReply(tweetId, "auth");
		verify(tweetRepository).findByUserIdInOrderByCreatedAtDesc(List.of(followedUserId));
		verify(hashtagRepository).findHashtagsByTweetId(tweetId);
		verify(mentionRepository).findMentionsByTweetId(tweetId);
	}

	@Test
	public void getUserFeed_Fallback() {
		String cacheKey = "followed_cache::" + userId;
		List<UUID> followedIds = List.of(UUID.randomUUID());

		HashtagEntity hashtag = new HashtagEntity();
		hashtag.setId(UUID.randomUUID());
		hashtag.setText("#example");

		MentionEntity mention = new MentionEntity();
		mention.setId(UUID.randomUUID());
		mention.setUserId(followedIds.getFirst());

		when(redisTemplate.opsForValue()).thenReturn(reactiveValueOperations);
		when(reactiveValueOperations.get(cacheKey))
			.thenReturn(Mono.just("[\"" + followedIds.get(0).toString() + "\"]"));

		when(tweetRepository.findByUserIdInOrderByCreatedAtDesc(any())).thenReturn(Flux.just(tweetEntity));
		when(hashtagRepository.findHashtagsByTweetId(tweetId)).thenReturn(Flux.just(hashtag));
		when(mentionRepository.findMentionsByTweetId(tweetId)).thenReturn(Flux.just(mention));
		when(interactionClient.getLikesCount(tweetId, "auth")).thenReturn(Mono.just(10L));
		when(interactionClient.getRepliesCount(tweetId, "auth")).thenReturn(Mono.just(5L));
		when(interactionClient.getRetweetsCount(tweetId, "auth")).thenReturn(Mono.just(3L));
		when(interactionClient.getTopReply(tweetId, "auth")).thenReturn(Mono.just(new ReplyDto()));

		when(tweetMapper.mapEntityToDto(any(), any(), any(), any(), any(ReplyDto.class), any(), any())).thenReturn(tweetDto);

		StepVerifier.create(tweetService.getUserFeedWithCachedFollowed(userId, "auth", new Exception()))
			.expectNext(tweetDto)
			.verifyComplete();

		verify(reactiveValueOperations).get(cacheKey);
		verify(tweetRepository).findByUserIdInOrderByCreatedAtDesc(any());
		verify(hashtagRepository).findHashtagsByTweetId(tweetId);
		verify(mentionRepository).findMentionsByTweetId(tweetId);
	}

	@Test
	public void getTweet_Success() {
		UUID tweetId = UUID.randomUUID();
		UUID replyId = UUID.randomUUID();

		TweetEntity tweetEntity = new TweetEntity();
		tweetEntity.setId(tweetId);
		tweetEntity.setUserId(userId);
		tweetEntity.setContent("Sample Tweet Content");

		TweetDto tweetDto = new TweetDto();
		tweetDto.setId(tweetId);
		tweetDto.setContent("Sample Tweet Content");
		tweetDto.setLikesCount(10L);
		tweetDto.setRepliesCount(5L);
		tweetDto.setRetweetsCount(3L);

		ReplyDto replyDto = new ReplyDto();
		replyDto.setId(replyId);
		replyDto.setContent("Sample Reply");

		when(tweetRepository.findById(tweetId)).thenReturn(Mono.just(tweetEntity));
		when(interactionClient.getRepliesForTweet(tweetId, "auth")).thenReturn(Flux.just(replyDto));
		when(interactionClient.getLikesCount(tweetId, "auth")).thenReturn(Mono.just(10L));
		when(interactionClient.getRepliesCount(tweetId, "auth")).thenReturn(Mono.just(5L));
		when(interactionClient.getRetweetsCount(tweetId, "auth")).thenReturn(Mono.just(3L));

		when(hashtagRepository.findHashtagsByTweetId(tweetId)).thenReturn(Flux.empty());
		when(mentionRepository.findMentionsByTweetId(tweetId)).thenReturn(Flux.empty());

		when(tweetMapper.mapEntityToDto(tweetEntity, 10L, 5L, 3L, List.of(replyDto), List.of(), List.of()))
			.thenReturn(tweetDto);

		StepVerifier.create(tweetService.getTweet(tweetId, "auth"))
			.expectNext(tweetDto)
			.verifyComplete();

		verify(tweetRepository).findById(tweetId);
		verify(interactionClient).getRepliesForTweet(tweetId, "auth");
		verify(interactionClient).getLikesCount(tweetId, "auth");
		verify(interactionClient).getRepliesCount(tweetId, "auth");
		verify(interactionClient).getRetweetsCount(tweetId, "auth");
		verify(hashtagRepository).findHashtagsByTweetId(tweetId);
		verify(mentionRepository).findMentionsByTweetId(tweetId);
		verify(tweetMapper).mapEntityToDto(tweetEntity, 10L, 5L, 3L, List.of(replyDto), List.of(), List.of());
	}

	@Test
	public void getTweet_NotFound() {
		when(tweetRepository.findById(tweetId)).thenReturn(Mono.empty());

		StepVerifier.create(tweetService.getTweet(tweetId, "auth"))
			.expectErrorMatches(throwable -> throwable instanceof TweetNotFoundException &&
				throwable.getMessage().equals("Tweet not found for id: " + tweetId))
			.verify();
	}

	@Test
	public void getUserTweetsSummary_Success() {
		when(tweetRepository.findByUserId(userId)).thenReturn(Flux.just(tweetEntity));
		when(tweetMapper.mapEntityToDto(any())).thenReturn(tweetDto);

		StepVerifier.create(tweetService.getUserTweetsSummary(userId))
			.expectNext(tweetDto)
			.verifyComplete();
	}

	@Test
	public void createTweet_Success() {
		TweetCreationRequest request = new TweetCreationRequest();
		request.setId(tweetId);
		request.setContent("New Tweet Content");

		when(tweetMapper.mapCreationRequestToEntity(request)).thenReturn(tweetEntity);
		when(tweetRepository.save(any())).thenReturn(Mono.just(tweetEntity));
		when(mentionService.handleTweetCreationMentions(request)).thenReturn(Mono.empty());
		when(hashtagService.handleTweetCreationHashtags(request)).thenReturn(Mono.empty());
		when(tweetMapper.mapEntityToCreationDto(any())).thenReturn(tweetDto);

		StepVerifier.create(tweetService.createTweet(request))
			.expectNext(tweetDto)
			.verifyComplete();
	}

	@Test
	public void updateTweet_Success() {
		TweetUpdateRequest request = new TweetUpdateRequest();
		request.setId(tweetId);
		request.setUserId(userId);
		request.setContent("Updated Content");

		when(tweetRepository.findByIdAndUserId(tweetId, userId)).thenReturn(Mono.just(tweetEntity));
		when(tweetMapper.mapUpdateRequestToEntity(request, tweetEntity)).thenReturn(tweetEntity);
		when(tweetRepository.save(any())).thenReturn(Mono.just(tweetEntity));
		when(mentionService.handleTweetUpdateMentions(request)).thenReturn(Mono.empty());
		when(hashtagService.handleTweetUpdateHashtags(request)).thenReturn(Mono.empty());

		StepVerifier.create(tweetService.updateTweet(request))
			.verifyComplete();
	}

	@Test
	public void deleteTweet_Success() {
		when(mentionRepository.deleteByTweetId(tweetId)).thenReturn(Mono.empty());
		when(tweetHashtagRepository.deleteByTweetId(tweetId)).thenReturn(Mono.empty());
		when(tweetRepository.deleteById(tweetId)).thenReturn(Mono.empty());

		StepVerifier.create(tweetService.deleteTweet(tweetId))
			.verifyComplete();
	}

	@Test
	public void searchTweetsByHashtag_Success() {
		String hashtag = "#example";

		UserDto userDto = new UserDto();
		userDto.setId(userId);
		userDto.setUserName("SampleUser");

		when(tweetRepository.findByHashtag(hashtag)).thenReturn(Flux.just(tweetEntity));
		when(userService.getUserSummary(userId)).thenReturn(Mono.just(userDto));
		when(tweetMapper.mapEntityToDto(tweetEntity, userDto)).thenReturn(tweetDto);

		StepVerifier.create(tweetService.searchTweetsByHashtag(hashtag))
			.expectNext(tweetDto)
			.verifyComplete();
	}

	@Test
	public void getTweetSummary_Success() {
		when(tweetRepository.findById(tweetId)).thenReturn(Mono.just(tweetEntity));
		when(tweetMapper.mapEntityToDto(tweetEntity)).thenReturn(tweetDto);

		StepVerifier.create(tweetService.getTweetSummary(tweetId))
			.expectNext(tweetDto)
			.verifyComplete();
	}
}
