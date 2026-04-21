package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductCategory;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import pl.commercelink.starter.dynamodb.DynamoDbMetadataConverter;
import pl.commercelink.starter.dynamodb.Metadata;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;
import static pl.commercelink.taxonomy.BrandMapper.unifyBrand;

@DynamoDBTable(tableName = "ProductsV2")
public class Product {

    @DynamoDBHashKey(attributeName = "categoryId")
    private String categoryId;
    @DynamoDBRangeKey(attributeName = "productId")
    private String productId;

    @DynamoDBAttribute(attributeName = "pimId")
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "PimIdIndex", attributeName = "pimId")
    private String pimId;

    // indexed attributes
    @DynamoDBAttribute(attributeName = "ean")
    private String ean;
    @DynamoDBAttribute(attributeName = "mfn")
    private String manufacturerCode;
    @DynamoDBAttribute(attributeName = "brand")
    private String brand;
    @DynamoDBAttribute(attributeName = "label")
    private String label;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "category")
    @DynamoDBTypeConvertedEnum
    private ProductCategory category;
    @DynamoDBAttribute(attributeName = "enabled")
    private boolean enabled = true;

    @DynamoDBAttribute(attributeName = "customAttributesFilters")
    private List<ProductCustomAttributeFilter> customAttributesFilters = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "customAttributes")
    private List<ProductCustomAttribute> customAttributes = new LinkedList<>();

    @DynamoDBAttribute(attributeName = "recommendation")
    private String recommendation;
    @DynamoDBAttribute(attributeName = "quickFilters")
    private List<String> quickFilters = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "productPage")
    private String productPage;
    @DynamoDBAttribute(attributeName = "suggestedRetailPrice")
    private int suggestedRetailPrice;
    @DynamoDBAttribute(attributeName = "availabilityType")
    @DynamoDBTypeConvertedEnum
    private ProductAvailabilityType availabilityType;
    @DynamoDBAttribute(attributeName = "stockExpectedQty")
    private int stockExpectedQty;
    @DynamoDBAttribute(attributeName = "restockPricePromo")
    private int restockPricePromo;
    @DynamoDBAttribute(attributeName = "restockPriceStandard")
    private int restockPriceStandard;
    @DynamoDBAttribute(attributeName = "estimatedDeliveryDays")
    private int estimatedDeliveryDays;
    @DynamoDBAttribute(attributeName = "pricingGroup")
    private String pricingGroup;
    @DynamoDBTypeConverted(converter = DynamoDbMetadataConverter.class)
    private List<Metadata> metadata = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "marketplaces")
    private List<String> marketplaces = new LinkedList<>();

    public Product() {
    }

    public Product(String categoryId, ProductCategory category) {
        this.categoryId = categoryId;
        this.productId = UUID.randomUUID().toString();
        this.category = category;
    }

    public Product(String categoryId, String pimId, String ean, String manufacturerCode, String brand, String label, String name, ProductCategory category, String pricingGroup) {
        this(categoryId, category);

        this.pimId = pimId;
        this.ean = ean;
        this.manufacturerCode = manufacturerCode;
        this.brand = brand;
        this.label = label;
        this.name = name;
        this.pricingGroup = pricingGroup;

        this.availabilityType = ProductAvailabilityType.BasedOnSupply;
    }

    @DynamoDBIgnore
    public boolean isApprovedForMarketplace(String marketplace) {
        return marketplaces.contains(marketplace);
    }

    @DynamoDBIgnore
    public boolean hasAvailabilityType(ProductAvailabilityType other) {
        return availabilityType == other;
    }

    // required by DynamoDB

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getPimId() {
        return pimId;
    }

    public void setPimId(String pimId) {
        this.pimId = pimId;
    }

    public String getEan() {
        return unifyEan(ean);
    }

    public void setEan(String ean) {
        this.ean = unifyEan(ean);
    }

    public String getManufacturerCode() {
        return unifyMfn(manufacturerCode);
    }

    public void setManufacturerCode(String manufacturerCode) {
        this.manufacturerCode = unifyMfn(manufacturerCode);
    }

    public String getBrand() {
        return unifyBrand(brand);
    }

    public void setBrand(String brand) {
        this.brand = unifyBrand(brand);
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

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public List<String> getQuickFilters() {
        return quickFilters;
    }

    public void setQuickFilters(List<String> quickFilters) {
        this.quickFilters = quickFilters;
    }

    public String getProductPage() {
        return productPage;
    }

    public void setProductPage(String productPage) {
        this.productPage = productPage;
    }

    public int getSuggestedRetailPrice() {
        return suggestedRetailPrice;
    }

    public void setSuggestedRetailPrice(int suggestedRetailPrice) {
        this.suggestedRetailPrice = suggestedRetailPrice;
    }

    public ProductAvailabilityType getAvailabilityType() {
        return availabilityType;
    }

    public void setAvailabilityType(ProductAvailabilityType availabilityType) {
        this.availabilityType = availabilityType;
    }

    public int getStockExpectedQty() {
        return stockExpectedQty;
    }

    public void setStockExpectedQty(int stockExpectedQty) {
        this.stockExpectedQty = stockExpectedQty;
    }

    public int getRestockPricePromo() {
        return restockPricePromo;
    }

    public void setRestockPricePromo(int restockPricePromo) {
        this.restockPricePromo = restockPricePromo;
    }

    public int getRestockPriceStandard() {
        return restockPriceStandard;
    }

    public void setRestockPriceStandard(int restockPriceStandard) {
        this.restockPriceStandard = restockPriceStandard;
    }

    public int getEstimatedDeliveryDays() {
        return estimatedDeliveryDays;
    }

    public void setEstimatedDeliveryDays(int estimatedDeliveryDays) {
        this.estimatedDeliveryDays = estimatedDeliveryDays;
    }

    public String getPricingGroup() {
        return pricingGroup;
    }

    public void setPricingGroup(String pricingGroup) {
        this.pricingGroup = pricingGroup;
    }

    public List<Metadata> getMetadata() {
        return metadata;
    }

    public void setMetadata(List<Metadata> metadata) {
        this.metadata = metadata;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getMarketplaces() {
        return marketplaces;
    }

    public void setMarketplaces(List<String> marketplaces) {
        this.marketplaces = marketplaces;
    }
}
