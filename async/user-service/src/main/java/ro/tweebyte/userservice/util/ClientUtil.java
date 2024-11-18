package ro.tweebyte.userservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;

@Component
public class ClientUtil {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SneakyThrows
    public <T> T parseResponse(HttpResponse<String> response, Class<T> responseType) {
        return objectMapper.readValue(response.body(), responseType);
    }

}
