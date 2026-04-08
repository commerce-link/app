package pl.commercelink.offer;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ProductView {

    @JsonProperty("categoryId")
    private String categoryId;
    @JsonProperty("pimId")
    private String pimId;
    @JsonProperty("manufacturerCode")
    private String manufacturerCode;
    @JsonProperty("brand")
    private String brand;
    @JsonProperty("label")
    private String label;
    @JsonProperty("name")
    private String name;

    public ProductView(String categoryId, String pimId, String manufacturerCode, String brand, String label, String name) {
        this.categoryId = categoryId;
        this.pimId = pimId;
        this.manufacturerCode = manufacturerCode;
        this.brand = brand;
        this.label = label;
        this.name = name;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getPimId() {
        return pimId;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public String getBrand() {
        return brand;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }
}
