package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.Product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The products picked from the inventory, as the review table posts them back: one row per product. */
@Getter
@Setter
public class ProductsBulkAddForm {

    private List<Product> products = new ArrayList<>();

    public ProductsBulkAddForm() {
    }

    public ProductsBulkAddForm(List<Product> products) {
        this.products = products;
    }

    /** The id of a field of the row at {@code index}; an error is keyed by it, so the summary links to the field. */
    public static String fieldId(int index, String field) {
        return "product-" + index + "-" + field;
    }

    /**
     * @param categoryLabels the labels the category offers, or empty when it groups by nothing and any label passes
     * @param pricingGroups  the pricing groups of the category; a product must land in one of them
     */
    public Map<String, String> validate(List<String> categoryLabels, List<String> pricingGroups) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (products.isEmpty()) {
            errors.put("products", "catalog.products.review.none");
        }
        for (int index = 0; index < products.size(); index++) {
            Product product = products.get(index);
            if (StringUtils.isBlank(product.getName())) {
                errors.put(fieldId(index, "name"), "product.error.name.required");
            }
            if (!categoryLabels.isEmpty() && !categoryLabels.contains(product.getLabel())) {
                errors.put(fieldId(index, "label"), "product.error.label.notInList");
            }
            if (pricingGroups.stream().noneMatch(group -> group.equalsIgnoreCase(product.getPricingGroup()))) {
                errors.put(fieldId(index, "pricingGroup"), "product.error.group.unknown");
            }
        }
        return errors;
    }
}
