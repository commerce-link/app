package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.util.List;

/**
 * DynamoDB converter for the {@code enabledProductGroups} attribute: a plain list of category-group
 * key strings (stable enum names). It pins the stored representation as a {@code List<String>} —
 * byte-identical to the output the previous enum-list converter produced — so existing rows read
 * back unchanged with no migration.
 */
public class ProductGroupListConverter implements DynamoDBTypeConverter<List<String>, List<String>> {

    @Override
    public List<String> convert(List<String> groupKeys) {
        return groupKeys;
    }

    @Override
    public List<String> unconvert(List<String> groupKeys) {
        return groupKeys;
    }
}
