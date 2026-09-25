package pl.commercelink.receipts;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Receipt keys are {@code {orderId}:R{n}}: one key per issuing attempt, sent to the provider as its idempotency key
 * and limited by the contract to {@code [A-Za-z0-9:_-]{1,64}}. Order ids are UUIDs today; any other id is replaced
 * by a hash so the key stays valid.
 */
public final class ReceiptAttemptKeys {

    private static final Pattern PLAIN_ORDER_ID = Pattern.compile("[A-Za-z0-9_-]{1,58}");

    private ReceiptAttemptKeys() {
    }

    public static String of(String orderId, int attemptNo) {
        return orderPrefix(orderId) + "R" + attemptNo;
    }

    public static String orderPrefix(String orderId) {
        return orderPart(orderId) + ":";
    }

    public static String orderPart(String orderId) {
        if (PLAIN_ORDER_ID.matcher(orderId).matches()) {
            return orderId;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(orderId.getBytes(StandardCharsets.UTF_8));
            return "o" + HexFormat.of().formatHex(digest).substring(0, 32);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The order part of a key (the order id for plain ids). */
    public static String orderPartOf(String receiptKey) {
        int separator = receiptKey.lastIndexOf(":R");
        return separator < 0 ? receiptKey : receiptKey.substring(0, separator);
    }
}
