package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Accessors(chain = true)
@Builder
public class TweetSummaryDto implements Serializable {

    @JsonProperty(value = "tweet_id")
    private UUID tweetId;

    @JsonProperty(value = "likes_count")
    private Long likesCount;

    @JsonProperty(value = "replies_count")
    private Long repliesCount;

    @JsonProperty(value = "retweets_count")
    private Long retweetsCount;

    @JsonProperty(value = "top_reply")
    private ReplyDto topReply;

}
