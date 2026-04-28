package ro.tweebyte.tweetservice.entity;

import lombok.*;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("tweet_hashtag")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TweetHashtagEntity {

    @Column(value = "tweet_id")
    private UUID tweetId;

    @Column(value = "hashtag_id")
    private UUID hashtagId;

}
