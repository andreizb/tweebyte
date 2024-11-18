package ro.tweebyte.tweetservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("tweets")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class TweetEntity {

    @Id
    private UUID id;

    @Column(value = "user_id")
    private UUID userId;

    @Version
    private Long version;

    @Column
    private String content;

    @Column(value = "created_at")
    private LocalDateTime createdAt;

}
