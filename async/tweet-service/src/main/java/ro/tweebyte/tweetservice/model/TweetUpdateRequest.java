package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
@Accessors(chain = true)
public class TweetUpdateRequest implements TweetRequest {

    private UUID id;

    private UUID userId;

    @JsonProperty(value = "content")
    @Size(min = 10, message = "Content must be at least 10 characters long")
    private String content;

}
