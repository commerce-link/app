package pl.commercelink.offer;

import com.fasterxml.jackson.annotation.JsonProperty;
import pl.commercelink.taxonomy.ProductGroup;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.products.ProductCustomAttribute;
import pl.commercelink.products.ProductCustomAttributeFilter;

import java.util.LinkedList;
import java.util.List;

public class ProductDetailsView {

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
    @JsonProperty("group")
    private ProductGroup group;
    @JsonProperty("category")
    private ProductCategory category;

    // store specific, product related information
    @JsonProperty("recommendation")
    private String recommendation;
    @JsonProperty("customAttributesFilters")
    private List<ProductCustomAttributeFilter> customAttributesFilters = new LinkedList<>();
    @JsonProperty("customAttributes")
    private List<ProductCustomAttribute> customAttributes = new LinkedList<>();
    @JsonProperty("quickFilters")
    private List<String> quickFilters = new LinkedList<>();
    @JsonProperty("metadata")
    private List<Metadata> metadata = new LinkedList<>();


    public ProductDetailsView() {
    }

    public ProductDetailsView(String categoryId, String pimId, String manufacturerCode, String brand, String label, String name, ProductGroup group, ProductCategory category) {
        this.categoryId = categoryId;
        this.pimId = pimId;
        this.manufacturerCode = manufacturerCode;
        this.brand = brand;
        this.label = label;
        this.name = name;
        this.group = group;
        this.category = category;
    }

    // setters and getters for generally available information

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getPimId() {
        return pimId;
    }

    public void setPimId(String pimId) {
        this.pimId = pimId;
    }

    public String getManufacturerCode() {
        return manufacturerCode;
    }

    public void setManufacturerCode(String manufacturerCode) {
        this.manufacturerCode = manufacturerCode;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public ProductGroup getGroup() {
        return group;
    }

    public void setGroup(ProductGroup group) {
        this.group = group;
    }

    // setters and getters for manually provided information
    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public List<ProductCustomAttributeFilter> getCustomAttributesFilters() {
        return customAttributesFilters;
    }

    public void setCustomAttributesFilters(List<ProductCustomAttributeFilter> customAttributesFilters) {
        this.customAttributesFilters = customAttributesFilters;
    }

    public List<ProductCustomAttribute> getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(List<ProductCustomAttribute> customAttributes) {
        this.customAttributes = customAttributes;
    }

    public List<String> getQuickFilters() {
        return quickFilters;
    }

    public void setQuickFilters(List<String> quickFilters) {
        this.quickFilters = quickFilters;
    }

    public List<Metadata> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<Metadata> metadata) {
        this.metadata = metadata;
    }

}
