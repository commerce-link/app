package pl.commercelink.products;

import pl.commercelink.taxonomy.ProductGroup;

import pl.commercelink.starter.dynamodb.DynamoDbEnumListConverter;

public class ProductGroupListConverter extends DynamoDbEnumListConverter<ProductGroup> {
    public ProductGroupListConverter() {
        super(ProductGroup.class);
    }
}