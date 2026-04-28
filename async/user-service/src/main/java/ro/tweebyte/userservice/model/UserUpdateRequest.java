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

    private Boolean isPrivate;

    private LocalDate birthDate;

}
