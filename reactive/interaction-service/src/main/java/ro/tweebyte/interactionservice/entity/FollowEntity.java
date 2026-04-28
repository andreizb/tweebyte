package ro.tweebyte.interactionservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("follows")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class FollowEntity implements Persistable {

    @Id
    private UUID id;

    @Column(value = "created_at")
    private LocalDateTime createdAt;

    @Transient
    private boolean isInsertable;

    @Column(value = "follower_id")
    private UUID followerId;

    @Column(value = "followed_id")
    private UUID followedId;

    @Column(value = "status")
    private String status;

    @Override
    public boolean isNew() {
        return isInsertable || id == null;
    }

}
