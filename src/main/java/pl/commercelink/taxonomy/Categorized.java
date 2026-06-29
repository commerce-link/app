package pl.commercelink.taxonomy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

public interface Categorized {

    String SERVICES = "Services";

    String getCategoryKey();

    @DynamoDBIgnore
    default ProductCategory getCategory() {
        return getCategoryKey() == null ? null : ProductCategory.valueOf(getCategoryKey());
    }

    @DynamoDBIgnore
    default boolean hasCategoryKey(String key) {
        return getCategoryKey() != null && getCategoryKey().equals(key);
    }

    @DynamoDBIgnore
    default boolean hasGroupKey(String groupKey) {
        return getCategory() != null && getCategory().getProductGroup().name().equals(groupKey);
    }

    @DynamoDBIgnore
    default int getSequenceNumber() {
        return getCategory().ordinal();
    }

    @Deprecated
    @DynamoDBIgnore
    default boolean hasCategory(ProductCategory category) {
        return getCategory() == category;
    }
}
