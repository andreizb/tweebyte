package ro.tweebyte.userservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {

    private String userName;

    private String email;

    private String password;

    /**
     * aligns async with reactive's UserUpdateRequest, which already had
     * this field. Without it, async silently dropped biography updates from
     * PUT /users/{id} (returned 204 with no DB change). MapStruct's
     * mapRequestToEntity picks the field up automatically by name.
     */
    private String biography;

    private Boolean isPrivate;

    private LocalDate birthDate;

}
