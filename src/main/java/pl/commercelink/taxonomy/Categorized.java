package pl.commercelink.taxonomy;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;

public interface Categorized {

    String SERVICES = "Services";
    String OTHER = "Other";

    String getCategory();

    @DynamoDBIgnore
    default boolean hasCategory(String category) {
        return getCategory() != null && getCategory().equals(category);
    }

    @DynamoDBIgnore
    default boolean isService() {
        return hasCategory(SERVICES);
    }

    @DynamoDBIgnore
    default boolean isProduct() {
        return !isService();
    }

    @DynamoDBIgnore
    default boolean isServiceGroup() {
        return isService();
    }
}
