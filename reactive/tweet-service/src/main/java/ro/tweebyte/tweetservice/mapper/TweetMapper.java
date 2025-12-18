package ro.tweebyte.tweetservice.mapper;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import ro.tweebyte.tweetservice.entity.HashtagEntity;
import ro.tweebyte.tweetservice.entity.MentionEntity;
import ro.tweebyte.tweetservice.entity.TweetEntity;
import ro.tweebyte.tweetservice.model.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class TweetMapper {

    @Autowired
    private MentionMapper mentionMapper;

    @Autowired
    private HashtagMapper hashtagMapper;

    public TweetDto mapEntityToDto(TweetEntity tweetEntity) {
        if (tweetEntity == null) {
            return null;
        }
        return TweetDto.builder()
                .id(tweetEntity.getId())
                .userId(tweetEntity.getUserId())
                .content(tweetEntity.getContent())
                .createdAt(tweetEntity.getCreatedAt())
                .build();
    }

    public TweetDto mapEntityToDto(TweetEntity tweetEntity, UserDto userDto) {
        TweetDto tweetDto = mapEntityToDto(tweetEntity);
        tweetDto.setUser(userDto);
        return tweetDto;
    }

    public TweetEntity mapCreationRequestToEntity(TweetCreationRequest request) {
        TweetEntity tweetEntity = mapCreationRequestToTweetEntity(request);
        tweetEntity.setId(UUID.randomUUID());
        tweetEntity.setCreatedAt(LocalDateTime.now());
        return tweetEntity;
    }

    public TweetDto mapEntityToCreationDto(TweetEntity tweetEntity) {
        if (tweetEntity == null) {
            return null;
        }
        return TweetDto.builder()
                .id(tweetEntity.getId())
                .build();
    }

    public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount,
            ReplyDto topReply) {
        TweetDto tweetDto = mapEntityToDto(tweetEntity);
        tweetDto.setLikesCount(likesCount);
        tweetDto.setRepliesCount(repliesCount);
        tweetDto.setRetweetsCount(retweetsCount);
        tweetDto.setTopReply(topReply);
        return tweetDto;
    }

    public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount,
            ReplyDto topReply, List<HashtagEntity> hashtags, List<MentionEntity> mentions) {
        TweetDto tweetDto = mapEntityToDto(tweetEntity, likesCount, repliesCount, retweetsCount, topReply);
        if (mentions != null) {
            tweetDto.setMentions(mentions.stream().map(mentionMapper::mapEntityToDto).collect(Collectors.toSet()));
        }
        if (hashtags != null) {
            tweetDto.setHashtags(hashtags.stream().map(hashtagMapper::mapEntityToDto).collect(Collectors.toSet()));
        }
        return tweetDto;
    }

    public TweetDto mapEntityToDto(TweetEntity tweetEntity, Long likesCount, Long repliesCount, Long retweetsCount,
            List<ReplyDto> replies, List<MentionEntity> mentions, List<HashtagEntity> hashtags) {
        TweetDto tweetDto = mapEntityToDto(tweetEntity);
        if (mentions != null) {
            tweetDto.setMentions(mentions.stream().map(mentionMapper::mapEntityToDto).collect(Collectors.toSet()));
        }
        if (hashtags != null) {
            tweetDto.setHashtags(hashtags.stream().map(hashtagMapper::mapEntityToDto).collect(Collectors.toSet()));
        }
        tweetDto.setLikesCount(likesCount);
        tweetDto.setRepliesCount(repliesCount);
        tweetDto.setRetweetsCount(retweetsCount);
        tweetDto.setReplies(replies);
        return tweetDto;
    }

    public TweetEntity mapUpdateRequestToEntity(TweetUpdateRequest request, TweetEntity tweetEntity) {
        if (request == null) {
            return tweetEntity;
        }
        if (request.getContent() != null) {
            tweetEntity.setContent(request.getContent());
        }
        return tweetEntity;
    }

    protected TweetEntity mapCreationRequestToTweetEntity(TweetCreationRequest request) {
        if (request == null) {
            return null;
        }
        TweetEntity entity = new TweetEntity();
        entity.setUserId(request.getUserId());
        entity.setContent(request.getContent());
        return entity;
    }

}
