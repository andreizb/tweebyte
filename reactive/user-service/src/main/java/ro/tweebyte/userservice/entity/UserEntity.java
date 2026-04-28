package ro.tweebyte.userservice.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Table("users")
@Builder
public class UserEntity implements Persistable {

    @Id
    private UUID id;

    @Column("user_name")
    private String userName;

    @Column("email")
    private String email;

    @Column("biography")
    private String biography;

    @Column("password")
    private String password;

    @Column("birth_date")
    private LocalDate birthDate;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("is_private")
    private Boolean isPrivate;

    @Transient
    private boolean isInsertable;

    @Override
    public boolean isNew() {
        return isInsertable || id == null;
    }

}
