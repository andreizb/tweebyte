package ro.tweebyte.tweetservice.entity;

import lombok.*;

import jakarta.persistence.*;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "hashtags")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class HashtagEntity {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "text", nullable = false)
    private String text;

    @ManyToMany(mappedBy = "hashtags")
    private Set<TweetEntity> tweets;

}
