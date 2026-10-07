package pl.commercelink.baskets;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

/**
 * How long an offer still holds (offers list spec §3.4). The pill in the list and the "Ważność" filter of the
 * repository use these same boundaries, so an offer is never shown as "Wygasa" under the "Ważne" filter. EXPIRED
 * follows Basket.isExpired (now strictly after expiresAt).
 */
public enum OfferValidity {
    ACTIVE("active"), EXPIRING("expiring"), EXPIRED("expired"), NO_EXPIRY("noExpiry");

    public static final int EXPIRING_DAYS = 7;

    private final String param;

    OfferValidity(String param) {
        this.param = param;
    }

    public String param() {
        return param;
    }

    public static OfferValidity of(LocalDateTime expiresAt, LocalDateTime now) {
        if (expiresAt == null) {
            return NO_EXPIRY;
        }
        if (now.isAfter(expiresAt)) {
            return EXPIRED;
        }
        return expiresAt.isAfter(now.plusDays(EXPIRING_DAYS)) ? ACTIVE : EXPIRING;
    }

    public static Optional<OfferValidity> parse(String value) {
        String trimmed = value == null ? "" : value.trim();
        return Arrays.stream(values()).filter(v -> v.param.equalsIgnoreCase(trimmed)).findFirst();
    }
}
