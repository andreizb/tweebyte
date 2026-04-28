package ro.tweebyte.interactionservice.entity;

import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "replies")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ReplyEntity extends InteractionEntity {

    @Column(name = "tweet_id")
    private UUID tweetId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "content")
    private String content;

}
