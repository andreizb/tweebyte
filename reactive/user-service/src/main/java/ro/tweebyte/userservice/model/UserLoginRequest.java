package ro.tweebyte.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class UserLoginRequest {

    /**
     * validate the login payload up-front so both stacks return a
     * structured 400 with field errors when the body is malformed instead of
     * leaking through the auth path as a generic 401. Async already had these
     * annotations — adding them here restores per-stack symmetry.
     */
    @Email(message = "Email must be a valid email address")
    @JsonProperty(value = "email")
    private String email;

    @NotBlank(message = "Password is required")
    @JsonProperty(value = "password")
    private String password;

}
