package ro.tweebyte.tweetservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ro.tweebyte.tweetservice.service.MediaService;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping(path = "/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(value = "/filter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CompletableFuture<ResponseEntity<byte[]>> filterMedia(
            @RequestPart("file") MultipartFile file) {
        return mediaService.process(file);
    }

}
