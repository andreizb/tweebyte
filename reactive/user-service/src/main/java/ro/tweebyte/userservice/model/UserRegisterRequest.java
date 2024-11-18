package ro.tweebyte.userservice.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {

    @NotBlank(message = "Full name is required")
    private String userName;

    @Email(message = "Email must be a valid email address")
    private String email;

    @Size(max = 500, message = "Biography must not exceed 500 characters")
    private String biography;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String password;

    @NotNull(message = "Birth date is required")
    @DateTimeFormat(pattern = "dd/MM/yyyy")
    private LocalDate birthDate;

    private Boolean isPrivate;

}
