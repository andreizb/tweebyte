package ro.tweebyte.userservice.controller;

import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import ro.tweebyte.userservice.service.MediaService;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/media")
@AllArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @GetMapping("/")
    public CompletableFuture<ResponseEntity<StreamingResponseBody>> downloadMedia() {
        return mediaService.download();
    }

}
