package ro.tweebyte.tweetservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ro.tweebyte.tweetservice.service.MediaService;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaControllerTest {

    @Mock
    private MediaService mediaService;

    @InjectMocks
    private MediaController mediaController;

    @Test
    void filterMediaDelegatesToService() throws Exception {
        ResponseEntity<byte[]> body = ResponseEntity.ok(new byte[]{1, 2, 3});
        when(mediaService.process(any(MultipartFile.class)))
                .thenReturn(CompletableFuture.completedFuture(body));

        MultipartFile file = new MockMultipartFile(
                "file", "x.png", "image/png", new byte[]{0});

        CompletableFuture<ResponseEntity<byte[]>> result = mediaController.filterMedia(file);

        assertNotNull(result);
        assertEquals(HttpStatus.OK, result.get().getStatusCode());
        verify(mediaService).process(file);
    }
}
