package ro.tweebyte.userservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private String userName;

    private String email;

    private String biography;

    private Boolean isPrivate;

    private String password;

    private LocalDate birthDate;

}
