package pl.commercelink.orders;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
            String orderedAt = raw.substring(0, at);
            // the text is compared with the index key, so only what the index can hold is worth a query
            if (orderedAt.length() < 12) {
                LocalDate.parse(orderedAt);
            } else {
                LocalDateTime.parse(orderedAt);
            }
            return Optional.of(new ArchiveCursor(orderedAt, raw.substring(at + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
