package pl.commercelink.inventory.supplier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class FeedHash {

    private FeedHash() {
    }

    static String of(byte[] data, String fileExtension) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(fileExtension.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
