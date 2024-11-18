package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import ro.tweebyte.interactionservice.client.TweetClient;
import ro.tweebyte.interactionservice.model.TweetDto;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class TweetServiceTest {

    @Mock
    private TweetClient tweetClient;

    @InjectMocks
    private TweetService tweetService;

    @Test
    void testGetTweetSummary() {
        UUID tweetId = UUID.randomUUID();
        TweetDto expected = new TweetDto();

        when(tweetClient.getTweetSummary(eq(tweetId))).thenReturn(expected);

        TweetDto result = tweetService.getTweetSummary(tweetId);

        assertEquals(expected, result);
        verify(tweetClient).getTweetSummary(tweetId);
    }

    @Test
    void testGetUserTweetsSummary() {
        UUID userId = UUID.randomUUID();
        List<TweetDto> expected = Collections.singletonList(new TweetDto());

        when(tweetClient.getUserTweetsSummary(eq(userId))).thenReturn(expected);

        List<TweetDto> result = tweetService.getUserTweetsSummary(userId);

        assertEquals(expected, result);
        verify(tweetClient).getUserTweetsSummary(userId);
    }

    @Test
    void testGetPopularHashtags() {
        List<TweetDto.HashtagDto> expected = Collections.singletonList(new TweetDto.HashtagDto());
        when(tweetClient.getPopularHashtags()).thenReturn(expected);

        List<TweetDto.HashtagDto> result = tweetService.getPopularHashtags();

        assertEquals(expected, result);
        verify(tweetClient).getPopularHashtags();
    }

}