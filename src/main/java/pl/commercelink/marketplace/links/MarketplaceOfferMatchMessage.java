package pl.commercelink.marketplace.links;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * Body of {@code marketplace-offer-match-queue}, sent as JSON text: {@code SqsTemplate} tags an object payload with its
 * class, which a {@code String} listener cannot take.
 */
record MarketplaceOfferMatchMessage(String storeId, String marketplace) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Offer match message cannot be written: " + this, e);
        }
    }

    /** Empty for a body that is not this JSON or names no store or marketplace. */
    static Optional<MarketplaceOfferMatchMessage> parse(String body) {
        if (StringUtils.isBlank(body)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(MAPPER.readValue(body, MarketplaceOfferMatchMessage.class))
                    .filter(message -> StringUtils.isNotBlank(message.storeId()))
                    .filter(message -> StringUtils.isNotBlank(message.marketplace()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
