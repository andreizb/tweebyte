package ro.tweebyte.userservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisterRequest {

    @NotBlank(message = "User name is required")
    private String userName;

    @Email(message = "Email must be a valid email address")
    private String email;

    @Size(max = 500, message = "Biography must not exceed 500 characters")
    private String biography;

    private Boolean isPrivate = false;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @NotNull(message = "Birth date is required")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate birthDate;

}
