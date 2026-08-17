package pl.commercelink.marketplace;

public record MarketplaceExportRunFile(
        MarketplaceExportRunDocument document,
        byte[] raw) {
}
