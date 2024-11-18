package ro.tweebyte.tweetservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.exception.UserNotFoundException;
import ro.tweebyte.tweetservice.mapper.MentionMapper;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.MentionRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MentionService {

    private final MentionRepository mentionRepository;
    private final TweetRepository tweetRepository;
    private final MentionMapper mentionMapper;
    private final UserService userService;

    @Transactional
    public Mono<Void> handleTweetCreationMentions(TweetRequest request) {
        return tweetRepository.findById(request.getId())
            .switchIfEmpty(Mono.error(new TweetNotFoundException("Tweet not found with id " + request.getId())))
            .flatMapMany(tweetEntity -> {
                Set<String> mentionTokens = extractMentions(tweetEntity.getContent());
                return Flux.fromIterable(mentionTokens)
                    .flatMap(userName -> createMention(userName, tweetEntity));
            })
            .then();
    }

    @Transactional
    public Mono<Void> handleTweetUpdateMentions(TweetRequest request) {
        Set<String> newMentionUsernames = extractMentions(request.getContent());

        return mentionRepository.findMentionsByTweetId(request.getId())
            .collectList()
            .flatMapMany(existingMentions -> {
                Set<String> existingUsernames = existingMentions.stream()
                    .map(MentionEntity::getText)
                    .collect(Collectors.toSet());
                Set<String> mentionsToRemove = new HashSet<>(existingUsernames);
                mentionsToRemove.removeAll(newMentionUsernames);

                Set<String> mentionsToAdd = new HashSet<>(newMentionUsernames);
                mentionsToAdd.removeAll(existingUsernames);

                Mono<Void> deleteMentions = Flux.fromIterable(existingMentions)
                    .filter(mention -> mentionsToRemove.contains(mention.getText()))
                    .flatMap(mentionRepository::delete)
                    .then();

                Mono<Void> addMentions = Flux.fromIterable(mentionsToAdd)
                    .flatMap(username -> userService.getUserId(username)
                        .onErrorResume(e -> {
                            if (e instanceof UserNotFoundException) {
                                return Mono.empty();
                            }
                            return Mono.error(e);
                        })
                        .flatMap(userId -> mentionRepository.save(mentionMapper.mapFieldsToEntity(userId, username))))
                    .then();

                return Mono.when(deleteMentions, addMentions);
            }).then();
    }


    private Mono<MentionEntity> createMention(String userName, TweetEntity tweetEntity) {
        return userService.getUserId(userName)
            .flatMap(userId -> mentionRepository.save(mentionMapper.mapFieldsToEntity(userId, userName, tweetEntity)))
            .onErrorResume(e -> {
                if (e instanceof UserNotFoundException) {
                    return Mono.empty();
                }
                return Mono.error(e);
            });
    }

    private Set<String> extractMentions(String content) {
        Set<String> mentions = new HashSet<>();
        Pattern pattern = Pattern.compile("@\\w+");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String mention = matcher.group().substring(1);
            mentions.add(mention);
        }
        return mentions;
    }

}
