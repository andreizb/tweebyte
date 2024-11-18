package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class MentionDto {

    @JsonProperty(value = "id")
    private UUID id;

    @JsonProperty(value = "user_id")
    private UUID userId;

    @JsonProperty(value = "text")
    private String text;

}
