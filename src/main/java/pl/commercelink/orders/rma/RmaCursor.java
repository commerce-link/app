package pl.commercelink.orders.rma;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A position in the RMA list: the creation time as the key holds it and the RMA id. It is a bound of the key condition,
 * never a DynamoDB start key, so a token from an old address cannot make the query fail; it carries no store, which
 * always comes from the session.
 */
public record RmaCursor(String createdAt, String rmaId) {

    private static final char SEPARATOR = '~';
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public static RmaCursor of(RMA rma) {
        return new RmaCursor(RmaListSortKey.createdAtText(rma.getCreatedAt()), rma.getRmaId());
    }

    public String position() {
        return createdAt + "#" + rmaId;
    }

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((createdAt + SEPARATOR + rmaId).getBytes(StandardCharsets.UTF_8));
    }

    public static Optional<RmaCursor> decode(String token) {
        if (StringUtils.isBlank(token) || token.length() > 200) {
            return Optional.empty();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token.trim()), StandardCharsets.UTF_8);
            int at = raw.lastIndexOf(SEPARATOR);
            if (at <= 0 || at == raw.length() - 1) {
                return Optional.empty();
            }
            String createdAt = raw.substring(0, at);
            String rmaId = raw.substring(at + 1);
            if (!ID.matcher(rmaId).matches()) {
                return Optional.empty();
            }
            if (!RmaListSortKey.UNDATED.equals(createdAt)) {
                LocalDateTime.parse(createdAt);
            }
            return Optional.of(new RmaCursor(createdAt, rmaId));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            return Optional.empty();
        }
    }
}
