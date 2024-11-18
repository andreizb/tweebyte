package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.entity.TweetHashtagEntity;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.TweetCreationRequest;
import ro.tweebyte.tweetservice.model.TweetUpdateRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HashtagServiceTest {

    @InjectMocks
    private HashtagService hashtagService;

    @Mock
    private HashtagRepository hashtagRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private TweetHashtagRepository tweetHashtagRepository;

    @Mock
    private HashtagMapper hashtagMapper;

    private TweetCreationRequest tweetRequest;
    private TweetEntity tweetEntity;

    @BeforeEach
    void setup() {
        tweetRequest = new TweetCreationRequest();
        tweetRequest.setId(UUID.randomUUID());
        tweetRequest.setContent("Hello #hashtag1 #hashtag2");

        tweetEntity = new TweetEntity();
        tweetEntity.setId(tweetRequest.getId());
        tweetEntity.setContent(tweetRequest.getContent());
    }

    @Test
    void handleTweetCreationHashtags_Success() {
        when(tweetRepository.findById(tweetRequest.getId())).thenReturn(Mono.just(tweetEntity));
        when(hashtagRepository.findByText(any())).thenReturn(Mono.empty());
        when(hashtagMapper.mapTextToEntity(any())).thenReturn(new HashtagEntity());
        when(hashtagRepository.save(any())).thenReturn(Mono.just(new HashtagEntity()));
        when(tweetHashtagRepository.save(any())).thenReturn(Mono.just(new TweetHashtagEntity()));

        StepVerifier.create(hashtagService.handleTweetCreationHashtags(tweetRequest))
            .verifyComplete();

        verify(tweetRepository).findById(tweetRequest.getId());
        verify(hashtagRepository, times(2)).findByText(any());
        verify(hashtagRepository, times(2)).save(any());
        verify(tweetHashtagRepository, times(2)).save(any());
    }

    @Test
    void handleTweetCreationHashtags_TweetNotFound() {
        when(tweetRepository.findById(tweetRequest.getId())).thenReturn(Mono.empty());

        StepVerifier.create(hashtagService.handleTweetCreationHashtags(tweetRequest))
            .expectError(RuntimeException.class)
            .verify();

        verify(tweetRepository).findById(tweetRequest.getId());
        verifyNoInteractions(hashtagRepository);
    }

    @Test
    void computePopularHashtags_Success() {
        HashtagDto hashtagDto = new HashtagDto();
        hashtagDto.setText("popular");

        when(hashtagRepository.findPopularHashtags()).thenReturn(Flux.just(hashtagDto));

        StepVerifier.create(hashtagService.computePopularHashtags())
            .expectNextMatches(hashtag -> hashtag.getText().equals("popular"))
            .verifyComplete();

        verify(hashtagRepository).findPopularHashtags();
    }

    @Test
    void handleTweetUpdateHashtags_Success() {
        UUID existingHashtagId = UUID.randomUUID();
        TweetHashtagEntity existingTweetHashtag = new TweetHashtagEntity(tweetRequest.getId(), existingHashtagId);
        HashtagEntity existingHashtag = new HashtagEntity();
        existingHashtag.setId(existingHashtagId);
        existingHashtag.setText("existingHashtag");

        HashtagEntity newHashtag = new HashtagEntity();
        newHashtag.setText("newHashtag");
        newHashtag.setId(UUID.randomUUID());
        TweetUpdateRequest request = new TweetUpdateRequest();
        request.setId(UUID.randomUUID());
        request.setContent("Updated content with #newHashtag");

        when(hashtagRepository.findByText("newHashtag")).thenReturn(Mono.empty());
        when(hashtagRepository.save(any())).thenReturn(Mono.just(newHashtag));
        when(tweetHashtagRepository.findByTweetId(request.getId()))
            .thenReturn(Flux.just(existingTweetHashtag));
        when(tweetHashtagRepository.deleteByTweetIdAndHashtagId(request.getId(), existingHashtagId))
            .thenReturn(Mono.empty());
        when(tweetHashtagRepository.save(any())).thenReturn(Mono.just(new TweetHashtagEntity(request.getId(), newHashtag.getId())));

        StepVerifier.create(hashtagService.handleTweetUpdateHashtags(request))
            .verifyComplete();

        verify(tweetHashtagRepository).findByTweetId(request.getId());
        verify(tweetHashtagRepository).deleteByTweetIdAndHashtagId(request.getId(), existingHashtagId);
        verify(tweetHashtagRepository).save(any(TweetHashtagEntity.class));
        verify(hashtagRepository).findByText("newHashtag");
        verify(hashtagRepository).save(any());
    }

    @Test
    void handleTweetUpdateHashtags_NoChanges() {
        UUID existingHashtagId = UUID.randomUUID();
        TweetHashtagEntity existingTweetHashtag = new TweetHashtagEntity(tweetRequest.getId(), existingHashtagId);
        HashtagEntity existingHashtag = new HashtagEntity();
        existingHashtag.setId(existingHashtagId);
        existingHashtag.setText("existingHashtag");

        TweetUpdateRequest request = new TweetUpdateRequest();
        request.setId(UUID.randomUUID());
        request.setContent("Content with #existingHashtag");

        when(hashtagRepository.findByText("existingHashtag")).thenReturn(Mono.just(existingHashtag));
        when(tweetHashtagRepository.findByTweetId(request.getId()))
            .thenReturn(Flux.just(existingTweetHashtag));

        StepVerifier.create(hashtagService.handleTweetUpdateHashtags(request))
            .verifyComplete();

        verify(tweetHashtagRepository).findByTweetId(request.getId());
        verifyNoMoreInteractions(tweetHashtagRepository);
        verify(hashtagRepository).findByText("existingHashtag");
        verifyNoMoreInteractions(hashtagRepository);
    }

}
