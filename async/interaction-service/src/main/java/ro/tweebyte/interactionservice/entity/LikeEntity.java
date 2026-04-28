package ro.tweebyte.interactionservice.entity;

import lombok.*;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "likes", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "user_id", "likeable_id", "likeable_type" })
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class LikeEntity extends InteractionEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "likeable_id")
    private UUID likeableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "likeable_type")
    private LikeableType likeableType;

    public enum LikeableType {
        TWEET, REPLY
    }

}
