package ro.tweebyte.interactionservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Branch-coverage tests for RetweetService — covers the null-tweet / not-found
 * arms missing from RetweetServiceTest's happy-path scenarios.
 */
@ExtendWith(MockitoExtension.class)
class RetweetServiceBranchTest {

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

    @Test
    void createRetweet_nullTweet_throws() {
        RetweetCreateRequest request = new RetweetCreateRequest();
        request.setOriginalTweetId(UUID.randomUUID());

        when(tweetService.getTweetSummary(any())).thenReturn(null);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> retweetService.createRetweet(request).get());

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(retweetRepository, never()).save(any());
    }

    @Test
    void updateRetweet_notFound_throws() {
        RetweetUpdateRequest request = new RetweetUpdateRequest();
        request.setId(UUID.randomUUID());

        when(retweetRepository.findById(any())).thenReturn(Optional.empty());

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> retweetService.updateRetweet(request).get());

        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        verify(retweetRepository, never()).save(any(RetweetEntity.class));
    }
}
