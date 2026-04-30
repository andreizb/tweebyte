package ro.tweebyte.tweetservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-unit tests for {@link MediaService}. The service is constructor-mockable
 * (no @Autowired); exercising it without Spring context keeps the test fast and
 * avoids context-startup side effects (port binding etc).
 */
class MediaServiceTest {

    private static byte[] makePng(int w, int h, int type) throws IOException {
        BufferedImage img = new BufferedImage(w, h, type);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        g.fillRect(2, 2, w - 4, h - 4);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void processReturnsJpegResponseEntityForRgbInput() throws Exception {
        MediaService svc = new MediaService();
        MultipartFile mf = new MockMultipartFile("file", "img.png", "image/png",
                makePng(8, 8, BufferedImage.TYPE_INT_RGB));

        ResponseEntity<byte[]> resp = svc.process(mf).get();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(MediaType.IMAGE_JPEG, resp.getHeaders().getContentType());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().length > 0);
        // Verify the bytes are a valid JPEG that decodes to the resize target (256x256).
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(resp.getBody()));
        assertNotNull(out, "produced bytes must be a valid JPEG image");
        assertEquals(256, out.getWidth());
        assertEquals(256, out.getHeight());
    }

    @Test
    void processConvertsNonRgbInputThroughToRGB() throws Exception {
        // ARGB exercises the toRGB() conversion branch (src.getType() != TYPE_INT_RGB).
        MediaService svc = new MediaService();
        MultipartFile mf = new MockMultipartFile("file", "img.png", "image/png",
                makePng(8, 8, BufferedImage.TYPE_INT_ARGB));

        ResponseEntity<byte[]> resp = svc.process(mf).get();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        BufferedImage out = ImageIO.read(new ByteArrayInputStream(resp.getBody()));
        assertEquals(256, out.getWidth());
        assertEquals(256, out.getHeight());
    }

    @Test
    void processThrowsWhenFileBytesAreNotAnImage() {
        MediaService svc = new MediaService();
        MultipartFile mf = new MockMultipartFile("file", "garbage.bin",
                "application/octet-stream", "not an image".getBytes());

        // ImageIO.read returns null on un-decodable input; service wraps the IOException
        // into RuntimeException inside the CompletableFuture.
        CompletableFuture<ResponseEntity<byte[]>> future = svc.process(mf);
        ExecutionException ee = assertThrows(ExecutionException.class, future::get);
        assertNotNull(ee.getCause());
        assertInstanceOf(RuntimeException.class, ee.getCause());
    }

    @Test
    void processThrowsWhenGetBytesFails() {
        // MultipartFile.getBytes() throws IOException → caught and rewrapped as RuntimeException.
        MediaService svc = new MediaService();
        MultipartFile bad = new MockMultipartFile("file", "x", "image/png", new byte[]{0}) {
            @Override
            public byte[] getBytes() throws IOException {
                throw new IOException("forced");
            }
        };

        CompletableFuture<ResponseEntity<byte[]>> future = svc.process(bad);
        ExecutionException ee = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(RuntimeException.class, ee.getCause());
    }
}
