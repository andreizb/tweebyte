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
import ro.tweebyte.interactionservice.entity.ReplyEntity;
import ro.tweebyte.interactionservice.mapper.ReplyMapper;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.repository.LikeRepository;
import ro.tweebyte.interactionservice.repository.ReplyRepository;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplyServiceTest {

	@InjectMocks
	private ReplyService replyService;

	@Mock
	private TweetService tweetService;

	@Mock
	private UserService userService;

	@Mock
	private ReplyRepository replyRepository;

	@Mock
	private LikeRepository likeRepository;

	@Mock
	private ReplyMapper replyMapper;

	private UUID userId;
	private UUID tweetId;
	private UUID replyId;
	private ReplyEntity replyEntity;
	private ReplyDto replyDto;
	private ReplyCreateRequest createRequest;
	private ReplyUpdateRequest updateRequest;

	@BeforeEach
	void setUp() {
		userId = UUID.randomUUID();
		tweetId = UUID.randomUUID();
		replyId = UUID.randomUUID();

		replyEntity = ReplyEntity.builder()
			.id(replyId)
			.userId(userId)
			.tweetId(tweetId)
			.content("Test Reply")
			.build();

		replyDto = new ReplyDto();
		replyDto.setId(replyId);
		replyDto.setContent("Test Reply");

		createRequest = new ReplyCreateRequest();
		createRequest.setTweetId(tweetId);
		createRequest.setUserId(userId);
		createRequest.setContent("Test Reply");

		updateRequest = new ReplyUpdateRequest();
		updateRequest.setId(replyId);
		updateRequest.setUserId(userId);
		updateRequest.setContent("Updated Reply");
	}

	@Test
	void createReply_Success() {
		when(tweetService.getTweetSummary(eq(tweetId))).thenReturn(Mono.just(new TweetDto()));
		when(replyMapper.mapRequestToEntity(eq(createRequest))).thenReturn(replyEntity);
		when(replyRepository.save(any(ReplyEntity.class))).thenReturn(Mono.just(replyEntity));
		when(replyMapper.mapEntityToCreationDto(any(ReplyEntity.class))).thenReturn(replyDto);

		StepVerifier.create(replyService.createReply(createRequest))
			.expectNext(replyDto)
			.verifyComplete();

		verify(tweetService, times(1)).getTweetSummary(tweetId);
		verify(replyRepository, times(1)).save(replyEntity);
		verify(replyMapper, times(1)).mapEntityToCreationDto(replyEntity);
	}

	@Test
	void updateReply_Success() {
		when(replyRepository.findById(eq(replyId))).thenReturn(Mono.just(replyEntity));
		doNothing().when(replyMapper).mapRequestToEntity(eq(updateRequest), eq(replyEntity));
		when(replyRepository.save(any(ReplyEntity.class))).thenReturn(Mono.just(replyEntity));

		StepVerifier.create(replyService.updateReply(updateRequest))
			.verifyComplete();

		verify(replyRepository, times(1)).findById(replyId);
		verify(replyMapper, times(1)).mapRequestToEntity(updateRequest, replyEntity);
		verify(replyRepository, times(1)).save(replyEntity);
	}

	@Test
	void deleteReply_Success() {
		when(replyRepository.findById(eq(replyId))).thenReturn(Mono.just(replyEntity));
		when(replyRepository.deleteById(eq(replyId))).thenReturn(Mono.empty());

		StepVerifier.create(replyService.deleteReply(userId, replyId))
			.verifyComplete();

		verify(replyRepository, times(1)).findById(replyId);
		verify(replyRepository, times(1)).deleteById(replyId);
	}

	@Test
	void getRepliesForTweet_Success() {
		when(replyRepository.findByTweetIdOrderByCreatedAtDesc(eq(tweetId))).thenReturn(Flux.just(replyEntity));
		when(userService.getUserSummary(eq(userId))).thenReturn(Mono.just(new UserDto(userId, "Test User", true, LocalDateTime.now())));
		when(replyMapper.mapEntityToDto(eq(replyEntity), eq("Test User"))).thenReturn(replyDto);

		StepVerifier.create(replyService.getRepliesForTweet(tweetId))
			.expectNext(replyDto)
			.verifyComplete();

		verify(replyRepository, times(1)).findByTweetIdOrderByCreatedAtDesc(tweetId);
		verify(userService, times(1)).getUserSummary(userId);
		verify(replyMapper, times(1)).mapEntityToDto(replyEntity, "Test User");
	}

	@Test
	void getReplyCountForTweet_Success() {
		when(replyRepository.countByTweetId(eq(tweetId))).thenReturn(Mono.just(10L));

		StepVerifier.create(replyService.getReplyCountForTweet(tweetId))
			.expectNext(10L)
			.verifyComplete();

		verify(replyRepository, times(1)).countByTweetId(tweetId);
	}

	@Test
	void getTopReplyForTweet_Success() {
		when(replyRepository.findTopReplyByLikesForTweetId(eq(tweetId))).thenReturn(Flux.just(replyEntity));
		when(userService.getUserSummary(eq(userId))).thenReturn(Mono.just(new UserDto(userId, "Test User", true, LocalDateTime.now())));
		when(replyMapper.mapEntityToDto(eq(replyEntity), eq("Test User"))).thenReturn(replyDto);

		StepVerifier.create(replyService.getTopReplyForTweet(tweetId))
			.expectNext(replyDto)
			.verifyComplete();

		verify(replyRepository, times(1)).findTopReplyByLikesForTweetId(tweetId);
		verify(userService, times(1)).getUserSummary(userId);
		verify(replyMapper, times(1)).mapEntityToDto(replyEntity, "Test User");
	}
}
