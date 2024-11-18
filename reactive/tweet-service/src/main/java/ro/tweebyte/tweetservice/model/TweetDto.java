package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class TweetDto {

    @JsonProperty(value = "id")
    private UUID id;

    @JsonProperty(value = "user_id")
    private UUID userId;

    @JsonProperty(value = "content")
    private String content;

    @JsonProperty(value = "created_at")
    private LocalDateTime createdAt;

    @JsonProperty(value = "mentions")
    private Set<MentionDto> mentions;

    @JsonProperty(value = "hashtags")
    private Set<HashtagDto> hashtags;

    @JsonProperty(value = "likes_count")
    private Long likesCount;

    @JsonProperty(value = "replies_count")
    private Long repliesCount;

    @JsonProperty(value = "retweets_count")
    private Long retweetsCount;

    @JsonProperty(value = "top_reply")
    private ReplyDto topReply;

    @JsonProperty(value = "replies")
    private List<ReplyDto> replies;

    @JsonProperty(value = "user")
    private UserDto user;

}
