package pl.commercelink.notifications;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.stores.StoreNotificationType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class StoreNotificationIds {

    private static final int MESSAGE_HASH_LENGTH = 16;

    private StoreNotificationIds() {
    }

    // the id names what the notification is about, so the same event published twice addresses the same record
    public static String of(StoreNotificationType type, String object, String message) {
        String prefix = type == null ? "UNKNOWN" : type.name();
        return prefix + ":" + (StringUtils.isBlank(object) ? messageHash(message) : object);
    }

    private static String messageHash(String message) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Objects.toString(message, "").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, MESSAGE_HASH_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
