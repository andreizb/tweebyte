package ro.tweebyte.interactionservice.entity;

import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "retweets")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class RetweetEntity extends InteractionEntity {

    @Column(name = "original_tweet_id")
    private UUID originalTweetId;

    @Column(name = "retweeter_id")
    private UUID retweeterId;

    @Column(name = "content")
    private String content;

}
