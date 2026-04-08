package pl.commercelink.checkout;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.StringUtils;

public class CheckoutItem {

    @JsonProperty("itemId")
    private String itemId;
    @JsonProperty("qty")
    private int qty;
    @JsonProperty("listed")
    private boolean listed = true;
    @JsonProperty("catalogId")
    private String catalogId;
    @JsonProperty("pricelistId")
    private String pricelistId;

    public String getItemId() {
        return itemId;
    }

    public int getQty() {
        return qty;
    }

    public boolean isListed() {
        return listed;
    }

    public String getCatalogId() {
        return catalogId;
    }

    public String getPricelistId() {
        return pricelistId;
    }

    public boolean hasCatalogId(String other) {
        return StringUtils.isNotBlank(catalogId) && catalogId.equals(other);
    }

    @JsonIgnore
    public boolean hasPricelistInformation() {
        return StringUtils.isNotBlank(catalogId) && StringUtils.isNotBlank(pricelistId);
    }
}
