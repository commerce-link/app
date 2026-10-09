package pl.commercelink.orders;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * A position in the archive index: the raw stored orderedAt (not reformatted, the index compares the stored text) and
 * the order id. It carries no store — the start key is always built with the session's store, so an edited token
 * cannot reach another store's partition.
 */
public record ArchiveCursor(String orderedAt, String orderId) {

    private static final char SEPARATOR = '~';

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((orderedAt + SEPARATOR + orderId).getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<ArchiveCursor> decode(String token) {
        if (StringUtils.isBlank(token) || token.length() > 200) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token.trim()), StandardCharsets.UTF_8);
            int at = raw.lastIndexOf(SEPARATOR);
            if (at <= 0 || at == raw.length() - 1) {
                return Optional.empty();
            }
            return Optional.of(new ArchiveCursor(raw.substring(0, at), raw.substring(at + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
