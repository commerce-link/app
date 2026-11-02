package pl.commercelink.marketplace.links;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * Body of {@code marketplace-offer-match-queue}, sent as JSON text: {@code SqsTemplate} tags an object payload with its
 * class, which a {@code String} listener cannot take. The operator's order has only the store and marketplace; a
 * continuation also names the run and the cursor it goes on from.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record MarketplaceOfferMatchMessage(String storeId, String marketplace, String runId, String cursor) {

    static MarketplaceOfferMatchMessage start(String storeId, String marketplace) {
        return new MarketplaceOfferMatchMessage(storeId, marketplace, null, null);
    }

    static MarketplaceOfferMatchMessage continuation(String storeId, String marketplace, String runId, String cursor) {
        return new MarketplaceOfferMatchMessage(storeId, marketplace, runId, cursor);
    }

    /** A message without a run (also every message sent before runs were resumable) starts a new run. */
    @JsonIgnore
    boolean isContinuation() {
        return StringUtils.isNotBlank(runId);
    }

    /**
     * SQS may deliver a message twice: only the copy at the run's saved cursor goes on, so two copies never read the
     * same pages side by side.
     */
    boolean continues(OfferMatchProgress progress) {
        return progress.status() == OfferMatchProgress.Status.RUNNING && runId.equals(progress.runId())
                && Objects.equals(cursor, progress.nextCursor());
    }

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
