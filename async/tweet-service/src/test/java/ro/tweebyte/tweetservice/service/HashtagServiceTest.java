package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagProjection;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
public class HashtagServiceTest {

    @Mock
    private HashtagRepository hashtagRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private HashtagMapper hashtagMapper;

    @InjectMocks
    private HashtagService hashtagService;

    @Test
    void testHandleTweetCreationHashtags() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setId(UUID.randomUUID());

        TweetEntity tweetEntity = new TweetEntity();
        tweetEntity.setContent("This is a #test tweet.");
        tweetEntity.setHashtags(new HashSet<>());

        HashtagEntity hashtagEntity = new HashtagEntity();
        hashtagEntity.setText("#test");

        when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.of(tweetEntity));
        when(hashtagMapper.mapTextToEntity(any(String.class))).thenReturn(hashtagEntity);

        hashtagService.handleTweetCreationHashtags(request);

        assertFalse(tweetEntity.getHashtags().isEmpty());
    }

    @Test
    void testHandleTweetCreationHashtagsWithTweetNotFound() {
        TweetCreationRequest request = new TweetCreationRequest();
        request.setId(UUID.randomUUID());

        when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(TweetNotFoundException.class, () -> hashtagService.handleTweetCreationHashtags(request));
    }

    @Test
    void testComputePopularHashtags() throws ExecutionException, InterruptedException {
        List<HashtagProjection> popularHashtags = Collections.emptyList();
        when(hashtagRepository.findPopularHashtags(any(Pageable.class))).thenReturn(popularHashtags);

        CompletableFuture<List<HashtagProjection>> result = hashtagService.computePopularHashtags();

        assertNotNull(result);
        assertEquals(popularHashtags, result.get());
    }
}