package ro.tweebyte.userservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class UserDto {

    @JsonProperty(value = "id")
    private UUID id;

    @JsonProperty(value = "user_name")
    private String userName;

    @JsonProperty(value = "email")
    private String email;

    @JsonProperty(value = "biography")
    private String biography;

    @JsonProperty(value = "is_private")
    private Boolean isPrivate;

    @JsonProperty(value = "birth_date")
    private LocalDate birthDate;

    @JsonProperty(value = "created_at")
    private LocalDateTime createdAt;

    @JsonProperty(value = "following")
    private Long following;

    @JsonProperty(value = "followers")
    private Long followers;

    @JsonProperty(value = "tweets")
    private List<TweetDto> tweets;

}
