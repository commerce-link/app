package pl.commercelink.pricelist;

import com.fasterxml.jackson.annotation.JsonProperty;

class PricelistEventPayload {
    @JsonProperty("storeId")
    private String storeId;
    @JsonProperty("catalogId")
    private String catalogId;

    PricelistEventPayload() {
    }

    PricelistEventPayload(String storeId, String catalogId) {
        this.storeId = storeId;
        this.catalogId = catalogId;
    }

    public String getStoreId() {
        return storeId;
    }

    public void setStoreId(String storeId) {
        this.storeId = storeId;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public void setCatalogId(String catalogId) {
        this.catalogId = catalogId;
    }
}
