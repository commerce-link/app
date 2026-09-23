package pl.commercelink.receipts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class ReceiptSnapshotJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();

    private ReceiptSnapshotJson() {
    }

    public static String write(ReceiptRequestSnapshot snapshot) {
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Receipt request snapshot could not be written", e);
        }
    }

    public static ReceiptRequestSnapshot read(String json) {
        try {
            return MAPPER.readValue(json, ReceiptRequestSnapshot.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Receipt request snapshot could not be read", e);
        }
    }
}
