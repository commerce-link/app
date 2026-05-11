package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductCategory;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.starter.util.UniqueIdentifierGenerator;
import pl.commercelink.starter.dynamodb.DeletionProtection;
import pl.commercelink.starter.dynamodb.Metadata;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@DynamoDBTable(tableName = "Catalogs")
public class ProductCatalog implements DeletionProtection {

    @DynamoDBHashKey(attributeName = "storeId")
    private String storeId;
    @DynamoDBRangeKey(attributeName = "catalogId")
    private String catalogId;
    @DynamoDBAttribute(attributeName = "name")
    private String name;
    @DynamoDBAttribute(attributeName = "deletionProtection")
    private boolean deletionProtection = true;
    @DynamoDBAttribute(attributeName = "categories")
    private List<CategoryDefinition> categories = new LinkedList<>();


    // required by DynamoDB
    public ProductCatalog() {
    }

    public ProductCatalog(String storeId, String name) {
        this.storeId = storeId;
        this.name = name;
        this.catalogId = UniqueIdentifierGenerator.generate();
    }

    public void addOrUpdateCategoryDefinition(CategoryDefinition categoryDefinition) {
        Optional<CategoryDefinition> existingCategory = categories.stream()
                .filter(c -> c.getCategoryId().equals(categoryDefinition.getCategoryId()))
                .findFirst();

        if (existingCategory.isPresent()){
                if(existingCategory.get().getType() != categoryDefinition.getType()){
                    categoryDefinition.setTypeChangedAt(LocalDateTime.now());
                }else{
                    categoryDefinition.setTypeChangedAt(existingCategory.get().getTypeChangedAt());
                }
        }

        categories.removeIf(c -> c.getCategoryId().equals(categoryDefinition.getCategoryId()));

        List<PriceDefinition> completePriceDefinitions = categoryDefinition.getPriceDefinitions().stream()
                .filter(PriceDefinition::isComplete)
                .collect(Collectors.toList());
        categoryDefinition.setPriceDefinitions(completePriceDefinitions);

        List<String> completeGroupingOrder = categoryDefinition.getGroupingOrder()
                .stream()
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .collect(Collectors.toList());
        categoryDefinition.setGroupingOrder(completeGroupingOrder);

        List<InventoryDefinition> completeInventoryDefinitions = categoryDefinition.getInventoryDefinitions()
                .stream()
                .filter(InventoryDefinition::isComplete)
                .peek(inventoryDefinition -> {
                    List<Metadata> completeMetadata = inventoryDefinition.getMetadata()
                            .stream()
                            .filter(Metadata::isComplete)
                            .collect(Collectors.toList());
                    inventoryDefinition.setMetadata(completeMetadata);
                })
                .collect(Collectors.toList());
        categoryDefinition.setInventoryDefinitions(completeInventoryDefinitions);

        List<MarketplaceDefinition> completeMarketplaceDefinitions = categoryDefinition.getMarketplaceDefinitions()
                .stream()
                .filter(MarketplaceDefinition::isComplete)
                .collect(Collectors.toList());
        categoryDefinition.setMarketplaceDefinitions(completeMarketplaceDefinitions);

        categories.add(categoryDefinition);

        categories.sort(Comparator.comparing(CategoryDefinition::getSequenceNumber));
    }


    public CategoryDefinition removeCategoryDefinition(String categoryId) {
        CategoryDefinition categoryDefinition = findCategoryDefinition(categoryId);
        if (categoryDefinition.isDeletionProtection()) {
            throw new RuntimeException("Cannot delete category definition with deletion protection");
        }
        categories.remove(categoryDefinition);
        return categoryDefinition;
    }

    public CategoryDefinition findCategoryDefinition(String categoryId) {
        return categories.stream()
                .filter(c -> c.getCategoryId().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Category definition not found"));
    }

    public CategoryDefinition findCategoryDefinition(ProductCategory category) {
        return categories.stream()
                .filter(c -> c.getName().equals(category.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Category definition not found"));
    }

    @Override
    public boolean isDeletionProtection() {
        return deletionProtection;
    }

    @DynamoDBIgnore
    public int getNextSequenceNumber() {
        return categories.stream()
                .map(CategoryDefinition::getSequenceNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    @DynamoDBIgnore
    public boolean isMarketplaceExportEnabled(String marketplace) {
        return categories.stream()
                .anyMatch(
                        c -> c.hasMarketplaceDefinition(marketplace)
                );
    }

    // required by DynamoDB
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDeletionProtection(boolean deletionProtection) {
        this.deletionProtection = deletionProtection;
    }

    public List<CategoryDefinition> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryDefinition> categories) {
        this.categories = categories;
    }


}
