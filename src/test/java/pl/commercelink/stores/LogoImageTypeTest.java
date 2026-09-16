package pl.commercelink.stores;

import org.junit.jupiter.api.Test;
import pl.commercelink.starter.storage.FileImageStorage;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class LogoImageTypeTest {

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] riff(String format) {
        byte[] result = new byte[16];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, result, 0, 4);
        System.arraycopy(bytes(0x24, 0x01, 0x00, 0x00), 0, result, 4, 4);
        System.arraycopy(format.getBytes(StandardCharsets.US_ASCII), 0, result, 8, 4);
        return result;
    }

    @Test
    void recognisesPngJpegGifAndWebpByTheirSignature() {
        // when / then
        assertThat(LogoImageType.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00))).contains(LogoImageType.PNG);
        assertThat(LogoImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0x00))).contains(LogoImageType.JPEG);
        assertThat(LogoImageType.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).contains(LogoImageType.GIF);
        assertThat(LogoImageType.detect("GIF87a....".getBytes(StandardCharsets.US_ASCII))).contains(LogoImageType.GIF);
        assertThat(LogoImageType.detect(riff("WEBP"))).contains(LogoImageType.WEBP);
    }

    @Test
    void rejectsTextSvgAndTruncatedFilesWhateverTheirName() {
        // when / then
        assertThat(LogoImageType.detect("hello".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(LogoImageType.detect("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(LogoImageType.detect(riff("WAVE"))).isEmpty();
        assertThat(LogoImageType.detect(bytes(0x89, 0x50))).isEmpty();
        assertThat(LogoImageType.detect(new byte[0])).isEmpty();
        assertThat(LogoImageType.detect(null)).isEmpty();
    }

    @Test
    void usesExtensionsTheLogoEndpointCanServe() {
        // when / then
        assertThat(LogoImageType.values()).extracting(LogoImageType::extension)
                .allMatch(FileImageStorage.EXTENSIONS::contains);
    }
}
