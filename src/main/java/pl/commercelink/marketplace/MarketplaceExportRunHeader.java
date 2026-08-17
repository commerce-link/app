package pl.commercelink.marketplace;

import java.time.LocalDateTime;

public record MarketplaceExportRunHeader(
        String marketplace,
        String catalogId,
        String runId,
        LocalDateTime storedAt) {
}
