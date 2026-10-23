package pl.commercelink.baskets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/** What the offers list narrows on (spec §5.2); `now` is passed in so the validity boundaries match the pills. */
public record OfferListCriteria(BasketType type, String text, Set<OfferValidity> validity, LocalDate from, LocalDate to,
                                LocalDateTime now) {

    public OfferListCriteria {
        validity = validity == null ? Set.of() : Set.copyOf(validity);
    }
}
