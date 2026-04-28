package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.entity.LikeEntity;
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.LikeMapper;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

	@InjectMocks
	private LikeService likeService;

	@Mock
	private UserService userService;

	@Mock
	private TweetService tweetService;

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private LikeMapper likeMapper;

	private UUID userId;
	private UUID tweetId;
	private UUID replyId;
	private LikeDto likeDto;
	private TweetDto tweetDto;
	private UserDto userDto;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		tweetId = UUID.randomUUID();
		replyId = UUID.randomUUID();

		likeDto = new LikeDto();
		tweetDto = new TweetDto();
		userDto = new UserDto();
	}

	@Test
	void getUserLikes_Success() {
		when(likeRepository.findByUserIdAndLikeableType(eq(userId), eq(LikeableType.TWEET.name())))
			.thenReturn(Flux.just(new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true, userId, tweetId, "TWEET")));
		when(tweetService.getTweetSummary(any(UUID.class)))
			.thenReturn(Mono.just(tweetDto));
		when(likeMapper.mapToDto(any(), eq(tweetDto)))
			.thenReturn(likeDto);

		StepVerifier.create(likeService.getUserLikes(userId))
			.expectNext(likeDto)
			.verifyComplete();

		verify(likeRepository, times(1)).findByUserIdAndLikeableType(userId, LikeableType.TWEET.name());
		verify(tweetService, atLeastOnce()).getTweetSummary(any(UUID.class));
		verify(likeMapper, atLeastOnce()).mapToDto(any(), eq(tweetDto));
	}

	@Test
	void getTweetLikes_Success() {
		when(likeRepository.findByLikeableIdAndLikeableType(eq(tweetId), eq(LikeableType.TWEET.name())))
			.thenReturn(Flux.just(new LikeEntity(UUID.randomUUID(), LocalDateTime.now(), true, userId, tweetId, "TWEET")));
		when(userService.getUserSummary(any(UUID.class)))
			.thenReturn(Mono.just(userDto));
		when(likeMapper.mapToDto(any(), eq(userDto)))
			.thenReturn(likeDto);

		StepVerifier.create(likeService.getTweetLikes(tweetId))
			.expectNext(likeDto)
			.verifyComplete();

		verify(likeRepository, times(1)).findByLikeableIdAndLikeableType(tweetId, LikeableType.TWEET.name());
		verify(userService, atLeastOnce()).getUserSummary(any(UUID.class));
		verify(likeMapper, atLeastOnce()).mapToDto(any(), eq(userDto));
	}

	@Test
	void getTweetLikesCount_Success() {
		when(likeRepository.countByLikeableIdAndLikeableType(eq(tweetId), eq(LikeableType.TWEET.name())))
			.thenReturn(Mono.just(5L));

		StepVerifier.create(likeService.getTweetLikesCount(tweetId))
			.expectNext(5L)
			.verifyComplete();

		verify(likeRepository, times(1)).countByLikeableIdAndLikeableType(tweetId, LikeableType.TWEET.name());
	}

	@Test
	void likeTweet_Success() {
		when(tweetService.getTweetSummary(eq(tweetId))).thenReturn(Mono.just(tweetDto));
		when(likeRepository.save(any())).thenReturn(Mono.just(new LikeEntity()));
		when(likeMapper.mapEntityToDto(any())).thenReturn(likeDto);

		StepVerifier.create(likeService.likeTweet(userId, tweetId))
			.expectNext(likeDto)
			.verifyComplete();

		verify(tweetService, times(1)).getTweetSummary(tweetId);
		verify(likeRepository, times(1)).save(any());
		verify(likeMapper, times(1)).mapEntityToDto(any());
	}

	@Test
	void unlikeTweet_Success() {
		when(likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(eq(userId), eq(tweetId), eq(LikeableType.TWEET.name())))
			.thenReturn(Mono.empty());

		StepVerifier.create(likeService.unlikeTweet(userId, tweetId))
			.verifyComplete();

		verify(likeRepository, times(1)).deleteByUserIdAndLikeableIdAndLikeableType(userId, tweetId, LikeableType.TWEET.name());
	}

	@Test
	void likeReply_Success() {
		when(replyRepository.findById(eq(replyId))).thenReturn(Mono.just(new ReplyEntity()));
		when(likeRepository.save(any())).thenReturn(Mono.just(new LikeEntity()));
		when(likeMapper.mapEntityToDto(any())).thenReturn(likeDto);

		StepVerifier.create(likeService.likeReply(userId, replyId))
			.expectNext(likeDto)
			.verifyComplete();

		verify(replyRepository, times(1)).findById(replyId);
		verify(likeRepository, times(1)).save(any());
		verify(likeMapper, times(1)).mapEntityToDto(any());
	}

	@Test
	void unlikeReply_Success() {
		when(likeRepository.deleteByUserIdAndLikeableIdAndLikeableType(eq(userId), eq(replyId), eq(LikeableType.REPLY.name())))
			.thenReturn(Mono.empty());

		StepVerifier.create(likeService.unlikeReply(userId, replyId))
			.verifyComplete();

		verify(likeRepository, times(1)).deleteByUserIdAndLikeableIdAndLikeableType(userId, replyId, LikeableType.REPLY.name());
	}
}
