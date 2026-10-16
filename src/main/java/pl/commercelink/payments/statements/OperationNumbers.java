package pl.commercelink.payments.statements;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The number of an operation whose bank exports none: derived from the account, day, amount, title and sender's
 * account, so overlapping statements give one operation the same number. Two identical operations of one day differ
 * by their ordinal within the file (the second one is "#2"); banks export whole days, so the ordinal is stable too.
 * Shown to people in the payment's "Nr operacji bankowej", hence short and without look-alike characters.
 */
final class OperationNumbers {

    private static final String PREFIX = "WB-";
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 10;

    private OperationNumbers() {
    }

    static String of(String key, int ordinal) {
        byte[] hash = sha256(key + "#" + ordinal);
        StringBuilder number = new StringBuilder(PREFIX);
        long bits = 0;
        int available = 0;
        int index = 0;
        while (number.length() < PREFIX.length() + LENGTH) {
            if (available < 5) {
                bits = (bits << 8) | (hash[index++] & 0xff);
                available += 8;
            }
            available -= 5;
            number.append(ALPHABET[(int) ((bits >> available) & 31)]);
        }
        return number.toString();
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
