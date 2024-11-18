package ro.tweebyte.tweetservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("mentions")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class MentionEntity implements Persistable {

    @Id
    private UUID id;

    @Column(value = "user_id")
    private UUID userId;

    private String text;

    @Column(value = "tweet_id")
    private UUID tweetId;

    @Transient
    private boolean isInsertable;

    @Override
    public boolean isNew() {
        return isInsertable || id == null;
    }

}
