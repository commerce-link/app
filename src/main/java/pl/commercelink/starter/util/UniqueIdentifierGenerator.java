package pl.commercelink.starter.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UniqueIdentifierGenerator {

    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int ID_LENGTH = 10;

    private UniqueIdentifierGenerator() {
        // Prevent instantiation
    }

    public static String generate() {
        StringBuilder identifier = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            int index = (int) (Math.random() * CHARACTERS.length());
            identifier.append(CHARACTERS.charAt(index));
        }
        return identifier.toString();
    }

    public static String generate(String input) {
        try {
            // Create a SHA-256 digest
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Hash the input string and get the byte array
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Create a StringBuilder to build the identifier
            StringBuilder identifier = new StringBuilder(ID_LENGTH);

            // Map the hash bytes to characters in the CHARACTERS set
            for (int i = 0; i < ID_LENGTH; i++) {
                int index = Byte.toUnsignedInt(hash[i]) % CHARACTERS.length();
                identifier.append(CHARACTERS.charAt(index));
            }

            return identifier.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}