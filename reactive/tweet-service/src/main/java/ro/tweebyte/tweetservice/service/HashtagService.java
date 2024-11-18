package ro.tweebyte.tweetservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetHashtagEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetHashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HashtagService {

    private final HashtagRepository hashtagRepository;
    private final TweetRepository tweetRepository;
    private final TweetHashtagRepository tweetHashtagRepository;
    private final HashtagMapper hashtagMapper;
    private final R2dbcEntityTemplate r2dbcEntityTemplate;

    @Transactional
    public Mono<Void> handleTweetCreationHashtags(TweetRequest request) {
        return tweetRepository.findById(request.getId())
            .switchIfEmpty(Mono.error(new RuntimeException("Tweet not found with id " + request.getId())))
            .flatMapMany(tweetEntity -> {
                    Set<String> hashtags = extractHashtags(tweetEntity.getContent());
                    return Flux.fromIterable(hashtags)
                        .flatMap(this::findOrCreateHashtag)
                        .flatMap(hashtag -> tweetHashtagRepository.save(new TweetHashtagEntity(tweetEntity.getId(), hashtag.getId())));
                }
            ).then();
    }

    @Transactional
    public Mono<Void> handleTweetUpdateHashtags(TweetRequest request) {
        Set<String> newHashtagsTexts = extractHashtags(request.getContent());

        Flux<HashtagEntity> newHashtags = Flux.fromIterable(newHashtagsTexts)
            .flatMap(this::findOrCreateHashtag);

        Flux<UUID> existingHashtagIds = tweetHashtagRepository.findByTweetId(request.getId())
            .map(TweetHashtagEntity::getHashtagId);

        return newHashtags.collectList().flatMapMany(newHashtagEntities -> {
            Set<UUID> newHashtagIds = newHashtagEntities.stream()
                .map(HashtagEntity::getId)
                .collect(Collectors.toSet());

            return existingHashtagIds.collectList().flatMapMany(existingIds -> {
                Set<UUID> hashtagsToRemove = new HashSet<>(existingIds);
                hashtagsToRemove.removeAll(newHashtagIds);

                Set<UUID> hashtagsToAdd = new HashSet<>(newHashtagIds);
                existingIds.forEach(hashtagsToAdd::remove);

                Mono<Void> removeOrphans = Flux.fromIterable(hashtagsToRemove)
                    .flatMap(hashtagId -> tweetHashtagRepository.deleteByTweetIdAndHashtagId(request.getId(), hashtagId))
                    .then();

                Mono<Void> addNew = Flux.fromIterable(hashtagsToAdd)
                    .flatMap(hashtagId -> tweetHashtagRepository.save(new TweetHashtagEntity(request.getId(), hashtagId)))
                    .then();

                return Mono.when(removeOrphans, addNew);
            });
        }).then();
    }

    private Mono<HashtagEntity> findOrCreateHashtag(String text) {
        return hashtagRepository.findByText(text)
            .switchIfEmpty(
                Mono.defer(() -> {
                    HashtagEntity newHashtag = hashtagMapper.mapTextToEntity(text);
                    return hashtagRepository.save(newHashtag);
                })
            );
    }

    private Set<String> extractHashtags(String content) {
        Set<String> hashtags = new HashSet<>();
        Pattern pattern = Pattern.compile("#\\w+");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String mention = matcher.group().substring(1);
            hashtags.add(mention);
        }
        return hashtags;
    }

    public Flux<HashtagDto> computePopularHashtags() {
        return hashtagRepository.findPopularHashtags();
    }

}
