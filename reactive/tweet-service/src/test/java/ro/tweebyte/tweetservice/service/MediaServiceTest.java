package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaServiceTest {

    private MediaService mediaService;

    @BeforeEach
    void setUp() {
        mediaService = new MediaService();
    }

    private FilePart filePartFrom(byte[] bytes) {
        FilePart part = mock(FilePart.class);
        DefaultDataBufferFactory factory = new DefaultDataBufferFactory();
        DataBuffer buffer = factory.wrap(bytes);
        when(part.content()).thenReturn(Flux.just(buffer));
        return part;
    }

    private byte[] tinyPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.fillRect(w / 4, h / 4, w / 2, h / 2);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private byte[] tinyJpeg(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.GRAY);
        g.fillRect(0, 0, w, h);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", baos);
        return baos.toByteArray();
    }

    @Test
    void processValidPngReturnsJpegBody() throws Exception {
        FilePart part = filePartFrom(tinyPng(64, 64));

        StepVerifier.create(mediaService.process(part))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals(MediaType.IMAGE_JPEG, response.getHeaders().getContentType());
                    byte[] body = response.getBody();
                    assertNotNull(body);
                    assertTrue(body.length > 0, "JPEG body should be non-empty");
                    // JPEG magic bytes 0xFFD8.
                    assertEquals((byte) 0xFF, body[0]);
                    assertEquals((byte) 0xD8, body[1]);
                })
                .verifyComplete();
    }

    @Test
    void processAlreadyRgbJpegSkipsToRgbConversion() throws Exception {
        // tinyJpeg returns a TYPE_INT_RGB image — exercises the early-return
        // branch in toRGB() (img.getType() == BufferedImage.TYPE_INT_RGB).
        FilePart part = filePartFrom(tinyJpeg(48, 48));

        StepVerifier.create(mediaService.process(part))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertTrue(response.getBody().length > 0);
                })
                .verifyComplete();
    }

    @Test
    void processInvalidImageBytesFailsWithRuntimeException() {
        FilePart part = filePartFrom(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});

        StepVerifier.create(mediaService.process(part))
                .expectErrorMatches(t -> t instanceof RuntimeException
                        && t.getCause() != null
                        && t.getCause().getMessage() != null
                        && t.getCause().getMessage().contains("Invalid image"))
                .verify();
    }
}
