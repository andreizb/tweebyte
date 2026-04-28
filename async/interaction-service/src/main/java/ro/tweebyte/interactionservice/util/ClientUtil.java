package ro.tweebyte.interactionservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import ro.tweebyte.interactionservice.exception.ClientException;

import java.net.http.HttpResponse;

@Component
@RequiredArgsConstructor
public class ClientUtil {

    private final ObjectMapper objectMapper;

    @SneakyThrows
    public  <T> T parseResponse(HttpResponse<String> response, Class<T> responseType) {
        if (response.statusCode() == 200) {
            return objectMapper.readValue(response.body(), responseType);
        }

        throw new ClientException(response);
    }

}
