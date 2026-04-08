package pl.commercelink.web.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GlobalInventoryAvailabilityAndPriceDto {

    @JsonProperty("lowestGrossPrice")
    private double lowestGrossPrice;
    @JsonProperty("totalAvailableQty")
    private long totalAvailableQty;

    private GlobalInventoryAvailabilityAndPriceDto() {
    }

    public GlobalInventoryAvailabilityAndPriceDto(double lowestGrossPrice, long totalAvailableQty) {
        this.lowestGrossPrice = lowestGrossPrice;
        this.totalAvailableQty = totalAvailableQty;
    }

    public double getLowestGrossPrice() {
        return lowestGrossPrice;
    }

    public long getTotalAvailableQty() {
        return totalAvailableQty;
    }
}
