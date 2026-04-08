package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductCategory;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.starter.dynamodb.DeletionProtection;
import pl.commercelink.starter.dynamodb.DynamoDbLocalDateTimeConverter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@DynamoDBDocument
public class CategoryDefinition implements DeletionProtection {

    @DynamoDBAttribute(attributeName = "categoryId")
    private String categoryId;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "category")
    @DynamoDBTypeConvertedEnum
    private ProductCategory category;
    @DynamoDBAttribute(attributeName = "type")
    @DynamoDBTypeConvertedEnum
    private CategoryDefinitionType type = CategoryDefinitionType.Managed;
    @DynamoDBAttribute(attributeName = "requiredDuringOrder")
    private boolean requiredDuringOrder;
    @DynamoDBAttribute(attributeName = "sequenceNumber")
    private int sequenceNumber;
    @DynamoDBAttribute(attributeName = "groupingOrder")
    private List<String> groupingOrder = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "maxQty")
    private int maxQty = 1;
    @DynamoDBAttribute(attributeName = "deletionProtection")
    private boolean deletionProtection = true;
    @DynamoDBAttribute(attributeName = "stockDefinition")
    private StockDefinition stockDefinition;
    @DynamoDBAttribute(attributeName = "priceDefinitions")
    private List<PriceDefinition> priceDefinitions = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "inventoryDefinitions")
    private List<InventoryDefinition> inventoryDefinitions = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "marketplaceDefinitions")
    private List<MarketplaceDefinition> marketplaceDefinitions = new LinkedList<>();
    @DynamoDBAttribute(attributeName = "availabilityDefinition")
    private AvailabilityDefinition availabilityDefinition;
    @DynamoDBAttribute(attributeName = "typeChangedAt")
    @DynamoDBTypeConverted(converter = DynamoDbLocalDateTimeConverter.class)
    private LocalDateTime typeChangedAt;

    // required by DynamoDB
    public CategoryDefinition() {
    }

    @DynamoDBIgnore
    public CategoryDefinition withGeneratedId() {
        this.categoryId = UniqueIdentifierGenerator.generate();
        return this;
    }

    public CategoryDefinition withName(String name) {
        this.name = name;
        return this;
    }

    public CategoryDefinition withMaxQty(int maxQty) {
        this.maxQty = maxQty;
        return this;
    }

    public CategoryDefinition withSequenceNumber(int seqNumber) {
        this.sequenceNumber = seqNumber;
        return this;
    }

    public CategoryDefinition withInventoryDefinition(InventoryDefinition inventoryDefinition) {
        this.inventoryDefinitions.add(inventoryDefinition);
        return this;
    }

    public CategoryDefinition withPriceDefinition(PriceDefinition priceDefinition) {
        this.priceDefinitions.add(priceDefinition);
        return this;
    }

    public CategoryDefinition withStockDefinition(StockDefinition stockDefinition) {
        this.stockDefinition = stockDefinition;
        return this;
    }

    public CategoryDefinition withMarketplaceDefinition(MarketplaceDefinition marketplaceDefinition) {
        this.marketplaceDefinitions.add(marketplaceDefinition);
        return this;
    }

    public CategoryDefinition withAvailabilityDefinition(AvailabilityDefinition availabilityDefinition) {
        this.availabilityDefinition = availabilityDefinition;
        return this;
    }

    public PriceDefinition findPriceDefinition(String pricingGroup) {
        return priceDefinitions.stream().filter(p -> p.hasPricingGroup(pricingGroup)).findFirst()
                .orElseThrow(() -> new RuntimeException("No price definition found for pricing group " + pricingGroup));
    }

    @DynamoDBIgnore
    public boolean hasCategory(ProductCategory category) {
        return this.category == category;
    }

    @DynamoDBIgnore
    public boolean hasType(CategoryDefinitionType type) {
        return this.type == type;
    }

    @DynamoDBIgnore
    public boolean isComplete() {
        return StringUtils.isNotBlank(name) && category != null && type != null && stockDefinition != null && stockDefinition.isComplete()
                && availabilityDefinition != null && availabilityDefinition.isComplete()
                && !priceDefinitions.isEmpty() && priceDefinitions.stream().anyMatch(PriceDefinition::isComplete);
    }

    @DynamoDBIgnore
    public boolean hasGrouping() {
        return !groupingOrder.isEmpty();
    }

    @DynamoDBIgnore
    public boolean hasEnabledMarketplaceDefinitions() {
        return marketplaceDefinitions.stream().anyMatch(MarketplaceDefinition::isEnabled);
    }

    @DynamoDBIgnore
    public boolean hasMarketplaceDefinition(String marketplace) {
        return marketplaceDefinitions.stream().anyMatch(m -> marketplace.equals(m.getName()) && m.isComplete() && m.isEnabled());
    }

    @DynamoDBIgnore
    public Optional<MarketplaceDefinition> getCategoryDefinition(String marketplace) {
        return marketplaceDefinitions.stream()
                .filter(m -> marketplace.equals(m.getName()))
                .findFirst();
    }

    // required by DynamoDB

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
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

    public boolean isRequiredDuringOrder() {
        return requiredDuringOrder;
    }

    public void setRequiredDuringOrder(boolean requiredDuringOrder) {
        this.requiredDuringOrder = requiredDuringOrder;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public List<String> getGroupingOrder() {
        return groupingOrder;
    }

    public void setGroupingOrder(List<String> groupingOrder) {
        this.groupingOrder = groupingOrder;
    }

    public int getMaxQty() {
        return maxQty;
    }

    public void setMaxQty(int maxQty) {
        this.maxQty = maxQty;
    }

    @Override
    public boolean isDeletionProtection() {
        return deletionProtection;
    }

    public void setDeletionProtection(boolean deletionProtection) {
        this.deletionProtection = deletionProtection;
    }

    public StockDefinition getStockDefinition() {
        return stockDefinition;
    }

    public void setStockDefinition(StockDefinition stockDefinition) {
        this.stockDefinition = stockDefinition;
    }

    public List<PriceDefinition> getPriceDefinitions() {
        return priceDefinitions;
    }

    public void setPriceDefinitions(List<PriceDefinition> priceDefinitions) {
        this.priceDefinitions = priceDefinitions.stream()
                .sorted(
                        Comparator
                                .comparing(PriceDefinition::getPriceMatch).reversed()
                                .thenComparing(PriceDefinition::getPricingGroup)
                ).collect(Collectors.toList());
    }

    public List<InventoryDefinition> getInventoryDefinitions() {
        return inventoryDefinitions;
    }

    public void setInventoryDefinitions(List<InventoryDefinition> inventoryDefinitions) {
        this.inventoryDefinitions = inventoryDefinitions;
    }

    public List<MarketplaceDefinition> getMarketplaceDefinitions() {
        return marketplaceDefinitions;
    }

    public void setMarketplaceDefinitions(List<MarketplaceDefinition> marketplaceDefinitions) {
        this.marketplaceDefinitions = marketplaceDefinitions;
    }

    public AvailabilityDefinition getAvailabilityDefinition() {
        return availabilityDefinition;
    }

    public void setAvailabilityDefinition(AvailabilityDefinition availabilityDefinition) {
        this.availabilityDefinition = availabilityDefinition;
    }

    public CategoryDefinitionType getType() {
        return type;
    }

    public void setType(CategoryDefinitionType type) {
        this.type = type;
    }

    public LocalDateTime getTypeChangedAt() {
        return typeChangedAt;
    }

    public void setTypeChangedAt(LocalDateTime typeChangedAt) {
        this.typeChangedAt = typeChangedAt;
    }
}
