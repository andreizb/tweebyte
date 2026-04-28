package ro.tweebyte.interactionservice.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.*;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.Objects;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RetweetServiceTest {

    @InjectMocks
    private RetweetService retweetService;

    @Mock
    private TweetService tweetService;

    @Mock
    private RetweetRepository retweetRepository;

    @Mock
    private RetweetMapper retweetMapper;

    @Mock
    private UserService userService;

    private final UUID originalTweetId = UUID.randomUUID();
    private final UUID retweetId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final RetweetCreateRequest createRequest = new RetweetCreateRequest();
    private final RetweetUpdateRequest updateRequest = new RetweetUpdateRequest();
    private final RetweetEntity retweetEntity = new RetweetEntity();
    private final TweetDto tweetDto = new TweetDto();
    private final UserDto userDto = new UserDto();
    private final RetweetDto retweetDto = new RetweetDto();

    @BeforeEach
    void setUp() {
        createRequest.setOriginalTweetId(originalTweetId);
        updateRequest.setId(retweetId);
        retweetEntity.setOriginalTweetId(retweetId);
        retweetEntity.setRetweeterId(userId);
    }

    @Test
    public void createRetweet_Success() {
        when(retweetMapper.mapRequestToEntity(any(RetweetCreateRequest.class)))
            .thenReturn(retweetEntity);
        when(tweetService.getTweetSummary(any(UUID.class)))
            .thenReturn(Mono.just(tweetDto));
        when(retweetRepository.save(any(RetweetEntity.class)))
            .thenReturn(Mono.just(retweetEntity));
        when(retweetMapper.mapEntityToDto(any(RetweetEntity.class)))
            .thenReturn(retweetDto);
        StepVerifier.create(retweetService.createRetweet(createRequest))
            .expectNextMatches(Objects::nonNull)
            .verifyComplete();
    }

    @Test
    public void updateRetweet_Success() {
        when(retweetRepository.findById(any(UUID.class)))
            .thenReturn(Mono.just(retweetEntity));
        when(retweetRepository.save(any(RetweetEntity.class)))
            .thenReturn(Mono.just(retweetEntity));
        doNothing().when(retweetMapper).mapRequestToEntity(updateRequest, retweetEntity);
        StepVerifier.create(retweetService.updateRetweet(updateRequest))
            .verifyComplete();
    }

    @Test
    public void deleteRetweet_Success() {
        when(retweetRepository.deleteById(any(UUID.class)))
            .thenReturn(Mono.empty());
        StepVerifier.create(retweetService.deleteRetweet(retweetId, userId))
            .verifyComplete();
    }

    @Test
    public void getRetweetsByUser_Success() {
        when(tweetService.getTweetSummary(any(UUID.class)))
            .thenReturn(Mono.just(tweetDto));
        when(retweetRepository.findByRetweeterId(any(UUID.class)))
            .thenReturn(Flux.just(retweetEntity));
        when(userService.getUserSummary(any(UUID.class)))
            .thenReturn(Mono.just(userDto));
        when(retweetMapper.mapEntityToDto(any(RetweetEntity.class), any(UserDto.class), any(TweetDto.class)))
            .thenReturn(retweetDto);

        StepVerifier.create(retweetService.getRetweetsByUser(userId))
            .expectNextMatches(dto -> dto.equals(retweetDto))
            .verifyComplete();
    }

    @Test
    public void getRetweetsOfTweet_Success() {
        when(retweetRepository.findByOriginalTweetId(any(UUID.class)))
            .thenReturn(Flux.just(retweetEntity));
        when(userService.getUserSummary(any(UUID.class)))
            .thenReturn(Mono.just(userDto));
        when(retweetMapper.mapEntityToDto(any(RetweetEntity.class), any(UserDto.class)))
            .thenReturn(retweetDto);


        StepVerifier.create(retweetService.getRetweetsOfTweet(originalTweetId))
            .expectNextMatches(dto -> dto.equals(retweetDto))
            .verifyComplete();
    }

    @Test
    public void getRetweetCountOfTweet_Success() {
        when(retweetRepository.countByOriginalTweetId(any(UUID.class)))
            .thenReturn(Mono.just(1L));
        StepVerifier.create(retweetService.getRetweetCountOfTweet(originalTweetId))
            .expectNext(1L)
            .verifyComplete();
    }
}
