package pl.commercelink.marketplace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketplaceExportRunDocument(
        int schemaVersion,
        String runId,
        Instant finishedAt,
        String status,
        List<String> failure,
        String storeId,
        String marketplace,
        String catalogId,
        String pricelistId,
        boolean providerCalled,
        List<MarketplaceOfferSnapshot> offers,
        List<MarketplaceExportExcludedItem> excluded) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_FAILED = "FAILED";

    public boolean wasSuccessful() {
        return STATUS_SUCCEEDED.equals(status);
    }

    public List<MarketplaceOfferSnapshot> offersOrEmpty() {
        return offers == null ? List.of() : offers;
    }

    public List<MarketplaceExportExcludedItem> excludedOrEmpty() {
        return excluded == null ? List.of() : excluded;
    }

    public static MarketplaceExportRunDocument fromCsvOffers(String runId, List<MarketplaceOfferSnapshot> offers) {
        return new MarketplaceExportRunDocument(
                CURRENT_SCHEMA_VERSION, runId, null, STATUS_SUCCEEDED, null,
                null, null, null, null, true, offers, List.of());
    }
}
