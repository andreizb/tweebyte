package ro.tweebyte.interactionservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("retweets")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class RetweetEntity implements Persistable {

    @Id
    private UUID id;

    @Column(value = "created_at")
    private LocalDateTime createdAt;

    @Column(value = "original_tweet_id")
    private UUID originalTweetId;

    @Column(value = "retweeter_id")
    private UUID retweeterId;

    @Column(value = "content")
    private String content;

    @Transient
    private boolean isInsertable;

    @Override
    public boolean isNew() {
        return isInsertable || id == null;
    }

}
