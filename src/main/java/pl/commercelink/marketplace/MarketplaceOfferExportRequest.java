package pl.commercelink.marketplace;

public class MarketplaceOfferExportRequest {

    private String marketplace;
    private String storeId;
    private String catalogId;
    private String pricelistId;

    public MarketplaceOfferExportRequest() {

    }

    public MarketplaceOfferExportRequest(String marketplace, String storeId, String catalogId, String pricelistId) {
        this.marketplace = marketplace;
        this.storeId = storeId;
        this.catalogId = catalogId;
        this.pricelistId = pricelistId;
    }

    public String getMarketplace() {
        return marketplace;
    }

    public String getStoreId() {
        return storeId;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public String getPricelistId() {
        return pricelistId;
    }
}
