package ro.tweebyte.tweetservice.entity;

import lombok.*;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "mentions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MentionEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "text", nullable = false)
    private String text;

    @ManyToOne
    @JoinColumn(name = "tweet_id", nullable = false)
    private TweetEntity tweetEntity;

}
