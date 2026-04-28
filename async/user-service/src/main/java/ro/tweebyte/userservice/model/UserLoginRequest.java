package ro.tweebyte.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public class UserLoginRequest {

    @Email(message = "Email must be a valid email address")
    @JsonProperty(value = "email")
    private String email;

    @NotBlank(message = "Password is required")
    @JsonProperty(value = "password")
    private String password;

}
