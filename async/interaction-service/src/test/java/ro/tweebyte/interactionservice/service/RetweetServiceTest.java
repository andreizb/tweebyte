package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
class RetweetServiceTest {

	@Mock
	private TweetService tweetService;

	@Mock
	private RetweetRepository retweetRepository;

	@Mock
	private RetweetMapper retweetMapper;

	@Mock
	private UserService userService;

	@InjectMocks
	private RetweetService retweetService;

	private final UUID userId = UUID.randomUUID();
	private final UUID tweetId = UUID.randomUUID();
	private final UUID retweetId = UUID.randomUUID();

	@Test
	void testCreateRetweet() throws ExecutionException, InterruptedException {
		RetweetCreateRequest request = new RetweetCreateRequest();
		TweetDto tweet = new TweetDto();

		when(tweetService.getTweetSummary(any())).thenReturn(tweet);
		when(retweetMapper.mapRequestToEntity(any())).thenReturn(new RetweetEntity());
		when(retweetRepository.save(any())).thenReturn(new RetweetEntity());
		when(retweetMapper.mapEntityToDto(any())).thenReturn(new RetweetDto());

		var result = retweetService.createRetweet(request).get();

		verify(tweetService).getTweetSummary(any());
		verify(retweetRepository).save(any());
		assertNotNull(result);
	}

	@Test
	void testUpdateRetweet() throws ExecutionException, InterruptedException {
		RetweetUpdateRequest request = new RetweetUpdateRequest();

		RetweetEntity retweet = new RetweetEntity();
		retweet.setId(retweetId);

		when(retweetRepository.findById(any())).thenReturn(Optional.of(retweet));

		retweetService.updateRetweet(request).get();

		verify(retweetRepository).findById(any());
		verify(retweetRepository).save(any());
	}

	@Test
	void testDeleteRetweet() throws ExecutionException, InterruptedException {
		retweetService.deleteRetweet(retweetId, userId).get();

		verify(retweetRepository).deleteById(retweetId);
	}

	@Test
	void testGetRetweetsByUser() throws ExecutionException, InterruptedException {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setOriginalTweetId(tweetId);

		when(retweetRepository.findByRetweeterId(userId)).thenReturn(Collections.singletonList(retweetEntity));
		when(tweetService.getTweetSummary(any())).thenReturn(new TweetDto());
		when(userService.getUserSummary(any())).thenReturn(new UserDto());
		when(retweetMapper.mapEntityToDto(any(), any(), any())).thenReturn(new RetweetDto());

		var result = retweetService.getRetweetsByUser(userId).get();

		verify(retweetRepository).findByRetweeterId(userId);
		verify(tweetService).getTweetSummary(any());
		verify(userService).getUserSummary(any());
		assertNotNull(result);
	}

	@Test
	void testGetRetweetsOfTweet() throws ExecutionException, InterruptedException {
		RetweetEntity retweetEntity = new RetweetEntity();
		retweetEntity.setRetweeterId(userId);

		when(retweetRepository.findByOriginalTweetId(tweetId)).thenReturn(Collections.singletonList(retweetEntity));
		when(userService.getUserSummary(any())).thenReturn(new UserDto());
		when(retweetMapper.mapEntityToDto(any(), any())).thenReturn(new RetweetDto());

		var result = retweetService.getRetweetsOfTweet(tweetId).get();

		verify(retweetRepository).findByOriginalTweetId(tweetId);
		verify(userService).getUserSummary(any());
		assertNotNull(result);
	}

	@Test
	void testGetRetweetCountOfTweet() throws ExecutionException, InterruptedException {
		when(retweetRepository.countByOriginalTweetId(tweetId)).thenReturn(5L);

		var result = retweetService.getRetweetCountOfTweet(tweetId).get();

		verify(retweetRepository).countByOriginalTweetId(tweetId);
		assertNotNull(result);
	}
}
