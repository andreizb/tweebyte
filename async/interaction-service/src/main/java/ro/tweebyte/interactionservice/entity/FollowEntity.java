package ro.tweebyte.interactionservice.entity;

import lombok.*;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "follows", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "follower_id", "followed_id" })
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class FollowEntity extends InteractionEntity {

    @Column(name = "follower_id")
    private UUID followerId;

    @Column(name = "followed_id")
    private UUID followedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Status status;

    public enum Status {
        PENDING, ACCEPTED, REJECTED
    }

}
