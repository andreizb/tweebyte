package ro.tweebyte.tweetservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.exception.TweetNotFoundException;
import ro.tweebyte.tweetservice.mapper.HashtagMapper;
import ro.tweebyte.tweetservice.model.HashtagDto;
import ro.tweebyte.tweetservice.model.HashtagProjection;
import ro.tweebyte.tweetservice.model.TweetRequest;
import ro.tweebyte.tweetservice.repository.HashtagRepository;
import ro.tweebyte.tweetservice.repository.TweetRepository;

import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HashtagService {

    private final HashtagRepository hashtagRepository;

    private final TweetRepository tweetRepository;

    private final HashtagMapper hashtagMapper;

    @Transactional
    public void handleTweetCreationHashtags(TweetRequest request) {
        TweetEntity tweetEntity = tweetRepository
            .findById(request.getId())
            .orElseThrow(() -> new TweetNotFoundException("Tweet not found with id " + request.getId()));

        Set<HashtagEntity> hashtags = extractHashtags(tweetEntity.getContent())
            .stream()
            .map(this::findOrCreateHashtag)
            .collect(Collectors.toSet());

        tweetEntity.getHashtags().clear();
        tweetEntity.getHashtags().addAll(hashtags);
    }

    @Async
    public CompletableFuture<List<HashtagProjection>> computePopularHashtags() {
        Pageable topHundred = PageRequest.of(0, 100, Sort.unsorted());
        List<HashtagProjection> hashtags = hashtagRepository.findPopularHashtags(topHundred);
        return CompletableFuture.completedFuture(hashtags);
    }

    private Set<String> extractHashtags(String content) {
        Set<String> hashtags = new HashSet<>();
        Pattern pattern = Pattern.compile("#\\w+");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            hashtags.add(matcher.group().substring(1));
        }

        return hashtags;
    }

    private HashtagEntity findOrCreateHashtag(String text) {
        return hashtagRepository
            .findByText(text)
            .orElseGet(() -> hashtagMapper.mapTextToEntity(text));
    }

}
