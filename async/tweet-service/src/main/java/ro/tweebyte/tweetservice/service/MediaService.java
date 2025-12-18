package ro.tweebyte.tweetservice.service;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.imageio.IIOImage;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.util.Iterator;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final ExecutorService executorService =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    public CompletableFuture<ResponseEntity<byte[]>> process(MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                byte[] input = file.getBytes();
                byte[] result = run(input);
                return ResponseEntity
                        .ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .body(result);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    private byte[] run(byte[] input) throws IOException {
        try (InputStream in = new ByteArrayInputStream(input)) {
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new IOException("Invalid image");
            BufferedImage blurred = toRGB(img);
            for (int i = 0; i < 3; ++i) {
                blurred = gaussianBlur(blurred, 5);
            }

            blurred = sobel(blurred);
            blurred = resize(blurred, 256, 256);

            return jpeg(blurred, 0.8f);
        }
    }

    private BufferedImage toRGB(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage img = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return img;
    }

    private BufferedImage gaussianBlur(BufferedImage src, int radius) {
        if (radius < 1) return src;
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        float[] kernel = gaussian(radius);
        int[] pixels = new int[w * h];
        int[] temp = new int[w * h];
        src.getRGB(0, 0, w, h, pixels, 0, w);

        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                float r = 0, g = 0, b = 0, sum = 0;
                for (int k = -radius; k <= radius; ++k) {
                    int xx = Math.min(w - 1, Math.max(0, x + k));
                    int rgb = pixels[y * w + xx];
                    float kval = kernel[k + radius];
                    r += ((rgb >> 16) & 0xff) * kval;
                    g += ((rgb >> 8) & 0xff) * kval;
                    b += (rgb & 0xff) * kval;
                    sum += kval;
                }
                int ir = Math.min(255, Math.max(0, Math.round(r / sum)));
                int ig = Math.min(255, Math.max(0, Math.round(g / sum)));
                int ib = Math.min(255, Math.max(0, Math.round(b / sum)));
                temp[y * w + x] = (ir << 16) | (ig << 8) | ib;
            }
        }

        for (int x = 0; x < w; ++x) {
            for (int y = 0; y < h; ++y) {
                float r = 0, g = 0, b = 0, sum = 0;
                for (int k = -radius; k <= radius; ++k) {
                    int yy = Math.min(h - 1, Math.max(0, y + k));
                    int rgb = temp[yy * w + x];
                    float kval = kernel[k + radius];
                    r += ((rgb >> 16) & 0xff) * kval;
                    g += ((rgb >> 8) & 0xff) * kval;
                    b += (rgb & 0xff) * kval;
                    sum += kval;
                }
                int ir = Math.min(255, Math.max(0, Math.round(r / sum)));
                int ig = Math.min(255, Math.max(0, Math.round(g / sum)));
                int ib = Math.min(255, Math.max(0, Math.round(b / sum)));
                dst.setRGB(x, y, (ir << 16) | (ig << 8) | ib);
            }
        }
        return dst;
    }

    private float[] gaussian(int r) {
        float[] kernel = new float[2 * r + 1];
        float sigma = r / 2.0f;
        float sum = 0;
        for (int i = -r; i <= r; ++i) {
            float val = (float) Math.exp(-(i * i) / (2 * sigma * sigma));
            kernel[i + r] = val;
            sum += val;
        }
        for (int i = 0; i < kernel.length; ++i) {
            kernel[i] /= sum;
        }
        return kernel;
    }

    private BufferedImage sobel(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] gx = {-1, 0, 1, -2, 0, 2, -1, 0, 1};
        int[] gy = {-1, -2, -1, 0, 0, 0, 1, 2, 1};
        for (int y = 1; y < h - 1; ++y) {
            for (int x = 1; x < w - 1; ++x) {
                int sxr = 0, sxg = 0, sxb = 0;
                int syr = 0, syg = 0, syb = 0;
                int idx = 0;
                for (int dy = -1; dy <= 1; ++dy) {
                    for (int dx = -1; dx <= 1; ++dx) {
                        int rgb = src.getRGB(x + dx, y + dy);
                        int r = (rgb >> 16) & 0xff;
                        int g = (rgb >> 8) & 0xff;
                        int b = rgb & 0xff;
                        sxr += gx[idx] * r;
                        sxg += gx[idx] * g;
                        sxb += gx[idx] * b;
                        syr += gy[idx] * r;
                        syg += gy[idx] * g;
                        syb += gy[idx] * b;
                        idx++;
                    }
                }
                int magr = Math.min(255, (int) Math.hypot(sxr, syr));
                int magg = Math.min(255, (int) Math.hypot(sxg, syg));
                int magb = Math.min(255, (int) Math.hypot(sxb, syb));
                dst.setRGB(x, y, (magr << 16) | (magg << 8) | magb);
            }
        }
        return dst;
    }

    private BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private byte[] jpeg(BufferedImage img, float q) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(q);
        }
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

}