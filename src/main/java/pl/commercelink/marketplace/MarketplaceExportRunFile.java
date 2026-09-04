package pl.commercelink.marketplace;

import java.util.List;

public record MarketplaceExportRunFile(
        String runId,
        boolean failed,
        List<MarketplaceOfferSnapshot> rows,
        byte[] raw) {
}
