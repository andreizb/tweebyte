package ro.tweebyte.interactionservice.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.net.http.HttpResponse;

@RequiredArgsConstructor
@Getter
public class ClientException extends RuntimeException {

    private final HttpResponse<String> response;

}
