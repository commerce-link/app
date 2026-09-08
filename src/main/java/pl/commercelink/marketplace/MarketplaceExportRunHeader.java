package pl.commercelink.marketplace;

public record MarketplaceExportRunHeader(
        String marketplace,
        String catalogId,
        String runId,
        boolean failed) {
}
