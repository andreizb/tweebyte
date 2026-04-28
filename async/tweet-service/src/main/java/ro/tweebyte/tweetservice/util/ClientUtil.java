package ro.tweebyte.tweetservice.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ro.tweebyte.tweetservice.exception.TweetException;

import java.net.http.HttpResponse;

@Component
@RequiredArgsConstructor
public class ClientUtil {

    private final ObjectMapper objectMapper;

    @SneakyThrows
    public String generateBody(Object body) {
        return objectMapper.writeValueAsString(body);
    }

    @SneakyThrows
    public  <T> T parseResponse(HttpResponse<String> response, Class<T> responseType) {
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), responseType);
        }

        throw new TweetException();
    }

    @SneakyThrows
    public <T> T parseResponse(HttpResponse<String> response, TypeReference<T> typeReference) {
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), typeReference);
        }

        throw new TweetException();
    }

}
