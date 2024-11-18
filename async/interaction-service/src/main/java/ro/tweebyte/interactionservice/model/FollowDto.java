package ro.tweebyte.interactionservice.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Accessors(chain = true)
public class FollowDto implements Serializable {

    @JsonProperty(value = "id")
    private UUID id;

    @JsonProperty(value = "user_name")
    private String userName;

    @JsonProperty(value = "follower_id")
    private UUID followerId;

    @JsonProperty(value = "followed_id")
    private UUID followedId;

    @JsonProperty(value = "created_at")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime createdAt;

    @JsonProperty(value = "status")
    private Status status;

    public enum Status {
        PENDING, ACCEPTED, REJECTED
    }

}
