package ro.tweebyte.tweetservice.entity;

import lombok.*;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
    name = "tweets",
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TweetEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Version
    private Long version;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "tweetEntity", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MentionEntity> mentions;

    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
        name = "tweet_hashtag",
        joinColumns = { @JoinColumn(name = "tweet_id") },
        inverseJoinColumns = { @JoinColumn(name = "hashtag_id") }
    )
    private Set<HashtagEntity> hashtags;

}
