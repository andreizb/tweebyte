package ro.tweebyte.tweetservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Builder
public class ReplyDto {

    @JsonProperty(value = "id")
    private UUID id;

    @JsonProperty(value = "user_id")
    private UUID userId;

    @JsonProperty(value = "user_name")
    private String userName;

    @JsonProperty(value = "content")
    private String content;

    @JsonProperty(value = "created_at")
    private LocalDateTime createdAt;

    @JsonProperty(value = "likes_count")
    private Long likesCount;

    @JsonProperty(value = "likes")
    private List<LikeDto> likes;

}
