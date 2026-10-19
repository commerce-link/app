package pl.commercelink.web.offers;

import pl.commercelink.baskets.BasketType;

import java.util.Arrays;
import java.util.Optional;

/** The three kinds of baskets the offers list shows, one segment each (spec D1). */
public enum OfferSegment {
    OFFERS("offers", BasketType.Offer),
    TEMPLATES("templates", BasketType.OfferTemplate),
    BASKETS("baskets", BasketType.Basket);

    private final String param;
    private final BasketType type;

    OfferSegment(String param, BasketType type) {
        this.param = param;
        this.type = type;
    }

    public String param() {
        return param;
    }

    public BasketType type() {
        return type;
    }

    public static OfferSegment of(BasketType type) {
        return Arrays.stream(values()).filter(s -> s.type == type).findFirst().orElse(OFFERS);
    }

    public static Optional<OfferSegment> parse(String value) {
        String trimmed = value == null ? "" : value.trim();
        return Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(trimmed)).findFirst();
    }
}
