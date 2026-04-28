package ro.tweebyte.userservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.service.MediaService;

@RestController
@RequestMapping(path = "/media")
@AllArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping(value = "/", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Void>> download(ServerWebExchange exchange) {
        return mediaService.download(exchange)
            .then(Mono.just(ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).build()));
    }

}
