package pl.commercelink.stores;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

/** Image formats accepted as a store logo, recognised by the file content rather than its name. */
public enum LogoImageType {
    PNG("png"),
    JPEG("jpg"),
    GIF("gif"),
    WEBP("webp");

    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87 = "GIF87a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] GIF89 = "GIF89a".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] RIFF = "RIFF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] WEBP_FORMAT = "WEBP".getBytes(StandardCharsets.US_ASCII);

    private final String extension;

    LogoImageType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static Optional<LogoImageType> detect(byte[] content) {
        if (content == null) {
            return Optional.empty();
        }
        if (startsWith(content, 0, PNG_SIGNATURE)) {
            return Optional.of(PNG);
        }
        if (startsWith(content, 0, JPEG_SIGNATURE)) {
            return Optional.of(JPEG);
        }
        if (startsWith(content, 0, GIF87) || startsWith(content, 0, GIF89)) {
            return Optional.of(GIF);
        }
        if (startsWith(content, 0, RIFF) && startsWith(content, 8, WEBP_FORMAT)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] content, int offset, byte[] signature) {
        return content.length >= offset + signature.length
                && Arrays.equals(content, offset, offset + signature.length, signature, 0, signature.length);
    }
}
