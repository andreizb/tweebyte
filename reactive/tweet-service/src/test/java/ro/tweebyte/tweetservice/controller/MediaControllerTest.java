package ro.tweebyte.tweetservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ro.tweebyte.tweetservice.service.MediaService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(SpringExtension.class)
@WebFluxTest(controllers = MediaController.class)
class MediaControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private MediaService mediaService;

    @Test
    void filterMediaDelegatesToService() {
        byte[] payload = new byte[]{(byte) 0xFF, (byte) 0xD8, 0x01, 0x02};
        ResponseEntity<byte[]> response = ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(payload);
        given(mediaService.process(any(FilePart.class))).willReturn(Mono.just(response));

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(new byte[]{1, 2, 3}) {
            @Override
            public String getFilename() {
                return "tiny.bin";
            }
        });

        webTestClient.post().uri("/media/filter")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(builder.build())
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.IMAGE_JPEG)
                .expectBody().consumeWith(res -> {
                    byte[] body = res.getResponseBody();
                    org.junit.jupiter.api.Assertions.assertNotNull(body);
                    org.junit.jupiter.api.Assertions.assertEquals(payload.length, body.length);
                });
    }
}
