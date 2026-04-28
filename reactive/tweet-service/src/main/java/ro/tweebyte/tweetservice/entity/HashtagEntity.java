package ro.tweebyte.tweetservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("hashtags")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class HashtagEntity implements Persistable {

    @Id
    private UUID id;

    private String text;

    @Transient
    private boolean isInsertable;

    @Override
    public boolean isNew() {
        return isInsertable || id == null;
    }

}
