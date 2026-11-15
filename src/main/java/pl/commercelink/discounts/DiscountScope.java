package pl.commercelink.discounts;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.baskets.BasketItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The products a discount or a promotion condition is about. An empty list does not narrow anything; a product has to
 * pass every list that is not empty, so categories "Monitory" with brands "Asus" means Asus monitors only.
 */
@DynamoDBDocument
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DiscountScope {

    @DynamoDBAttribute(attributeName = "categories")
    private List<String> categories = new ArrayList<>();
    @DynamoDBAttribute(attributeName = "brands")
    private List<String> brands = new ArrayList<>();
    @DynamoDBAttribute(attributeName = "products")
    private List<TargetValue> products = new ArrayList<>();

    @DynamoDBIgnore
    public boolean isEmpty() {
        return categories.isEmpty() && brands.isEmpty() && products.isEmpty();
    }

    /**
     * @param productAliases the ids a product is known by in the store (itself, its source, its clone); the products
     *                       list matches the item when any of them is on it
     */
    boolean matches(BasketItem item, Function<String, Set<String>> productAliases) {
        return item.isProduct()
                && (categories.isEmpty() || contains(categories, item.getCategory()))
                && (brands.isEmpty() || contains(brands, item.getBrand()))
                && (products.isEmpty() || containsProduct(item, productAliases));
    }

    private boolean containsProduct(BasketItem item, Function<String, Set<String>> productAliases) {
        if (item.getId() == null) {
            return false;
        }
        Set<String> ids = new HashSet<>(productAliases.apply(item.getId()));
        ids.add(item.getId());
        List<String> listed = products.stream().map(TargetValue::getId).toList();
        return ids.stream().anyMatch(id -> contains(listed, id));
    }

    private static boolean contains(List<String> values, String value) {
        String wanted = StringUtils.trim(value);
        return wanted != null && values.stream().anyMatch(v -> wanted.equals(StringUtils.trim(v)));
    }
}