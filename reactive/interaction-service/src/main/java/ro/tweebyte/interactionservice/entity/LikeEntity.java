package ro.tweebyte.interactionservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("likes")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class LikeEntity implements Persistable {

    @Id
    private UUID id;

    @Column(value = "created_at")
    private LocalDateTime createdAt;

    @Transient
    private boolean isInsertable;

    @Column(value = "user_id")
    private UUID userId;

    @Column(value = "likeable_id")
    private UUID likeableId;

    @Column(value = "likeable_type")
    private String likeableType;

    @Override
    public boolean isNew() {
        return isInsertable || id == null;
    }

}
