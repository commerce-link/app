package pl.commercelink.pricelist;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Body of the snapshot and aggregate queues. The external sender of the global trigger sends no payload, so anything
 * that is not this JSON means "global"; a store's own message carries the day of the run that fanned it out.
 */
public record PriceDataMessage(String storeId, String date) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    static PriceDataMessage forStore(String storeId, LocalDate date) {
        return new PriceDataMessage(storeId, date.toString());
    }

    static PriceDataMessage parse(String body) {
        if (body == null || body.isBlank()) {
            return new PriceDataMessage(null, null);
        }
        try {
            PriceDataMessage parsed = MAPPER.readValue(body, PriceDataMessage.class);
            return parsed != null ? parsed : new PriceDataMessage(null, null);
        } catch (Exception e) {
            return new PriceDataMessage(null, null);
        }
    }

    boolean isForStore() {
        return storeId != null && !storeId.isBlank();
    }

    LocalDate dayOrToday() {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return LocalDate.now();
        }
    }
}
