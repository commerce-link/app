package pl.commercelink.starter.email;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** The From header value SES accepts for a display name typed by a store admin or taken from the store name. */
final class FromHeader {

    private static final int MAX_BYTES_PER_WORD = 45;

    private FromHeader() {
    }

    static String of(String displayName, String address) {
        String name = StringUtils.trimToNull(displayName == null ? null : displayName.replaceAll("[\\r\\n]+", " "));
        if (name == null) {
            return address;
        }
        // SES requires a display name with non-ASCII characters (Polish store names) as RFC 2047 encoded words.
        if (!StandardCharsets.US_ASCII.newEncoder().canEncode(name)) {
            return encodedWords(name) + " <" + address + ">";
        }
        return "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\" <" + address + ">";
    }

    /**
     * An encoded word is at most 75 characters (RFC 2047 section 2): "=?UTF-8?B?" and "?=" leave 63 for Base64, so at
     * most 45 bytes of the name each. Words are cut on whole code points and separated by a space, which decoders drop
     * between adjacent encoded words.
     */
    private static String encodedWords(String name) {
        List<String> words = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        int chunkBytes = 0;
        for (int offset = 0; offset < name.length(); ) {
            int codePoint = name.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int bytes = character.getBytes(StandardCharsets.UTF_8).length;
            if (chunkBytes + bytes > MAX_BYTES_PER_WORD) {
                words.add(encodedWord(chunk.toString()));
                chunk.setLength(0);
                chunkBytes = 0;
            }
            chunk.append(character);
            chunkBytes += bytes;
            offset += Character.charCount(codePoint);
        }
        words.add(encodedWord(chunk.toString()));
        return String.join(" ", words);
    }

    private static String encodedWord(String text) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }
}
