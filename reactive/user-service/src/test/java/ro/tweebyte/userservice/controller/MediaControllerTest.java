package ro.tweebyte.userservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ro.tweebyte.userservice.service.MediaService;

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
    void downloadReturnsPartialContentWhenServiceCompletes() {
        given(mediaService.download(any())).willReturn(Mono.empty());

        webTestClient.get().uri("/media/")
                .exchange()
                .expectStatus().isEqualTo(206);
    }

    @Test
    void downloadPropagatesServiceFailureAs500() {
        given(mediaService.download(any())).willReturn(Mono.error(new RuntimeException("io fail")));

        webTestClient.get().uri("/media/")
                .exchange()
                .expectStatus().is5xxServerError();
    }
}
