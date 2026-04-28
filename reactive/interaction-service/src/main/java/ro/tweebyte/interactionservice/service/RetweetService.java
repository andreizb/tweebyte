package ro.tweebyte.interactionservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.interactionservice.entity.RetweetEntity;
import ro.tweebyte.interactionservice.mapper.RetweetMapper;
import ro.tweebyte.interactionservice.model.RetweetCreateRequest;
import ro.tweebyte.interactionservice.model.RetweetDto;
import ro.tweebyte.interactionservice.model.RetweetUpdateRequest;
import ro.tweebyte.interactionservice.repository.RetweetRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetweetService {

    private final TweetService tweetService;

    private final RetweetRepository retweetRepository;

    private final RetweetMapper retweetMapper;

    private final UserService userService;

    public Mono<RetweetDto> createRetweet(RetweetCreateRequest request) {
        return tweetService.getTweetSummary(request.getOriginalTweetId())
            .flatMap(tweet -> {
                RetweetEntity retweet = retweetMapper.mapRequestToEntity(request);
                return retweetRepository.save(retweet);
            })
            .map(retweetMapper::mapEntityToDto);
    }

    public Mono<Void> updateRetweet(RetweetUpdateRequest request) {
        return retweetRepository.findById(request.getId())
            .flatMap(retweet -> {
                retweetMapper.mapRequestToEntity(request, retweet);
                return retweetRepository.save(retweet);
            })
            .then();
    }

    public Mono<Void> deleteRetweet(UUID retweetId, UUID userId) {
        return retweetRepository.deleteById(retweetId);
    }

    public Flux<RetweetDto> getRetweetsByUser(UUID userId) {
        return retweetRepository.findByRetweeterId(userId)
            .flatMap(retweetEntity -> tweetService.getTweetSummary(retweetEntity.getOriginalTweetId())
                .zipWith(userService.getUserSummary(retweetEntity.getRetweeterId()))
                .map(tuple -> retweetMapper.mapEntityToDto(retweetEntity, tuple.getT2(), tuple.getT1())));
    }

    public Flux<RetweetDto> getRetweetsOfTweet(UUID tweetId) {
        return retweetRepository.findByOriginalTweetId(tweetId)
            .flatMap(retweetEntity -> userService.getUserSummary(retweetEntity.getRetweeterId())
                .map(userDto -> retweetMapper.mapEntityToDto(retweetEntity, userDto)));
    }

    public Mono<Long> getRetweetCountOfTweet(UUID tweetId) {
        return retweetRepository.countByOriginalTweetId(tweetId);
    }

}
