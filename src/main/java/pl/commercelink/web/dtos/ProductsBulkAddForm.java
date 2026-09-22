package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductAvailabilityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The products picked from the inventory, as the review table posts them back: one row per product. */
@Getter
@Setter
public class ProductsBulkAddForm {

    /**
     * One row of the review table. It carries only what the review shows or edits, so nothing else of a product can
     * be set from the request; the identity of the product -- its category, its id and its PIM entry -- is the
     * application's to give when the row is saved.
     */
    @Getter
    @Setter
    public static class Row {

        private String ean;
        private String manufacturerCode;
        private String brand;
        private String name;
        private String label;
        private String pricingGroup;
        private ProductAvailabilityType availabilityType;

        public Row() {
        }

        private Row(Product product) {
            this.ean = product.getEan();
            this.manufacturerCode = product.getManufacturerCode();
            this.brand = product.getBrand();
            this.name = product.getName();
            this.label = product.getLabel();
            this.pricingGroup = product.getPricingGroup();
            this.availabilityType = product.getAvailabilityType();
        }

        /** The product as it is created; the PIM entry is resolved by the controller, never taken from the row. */
        public Product toProduct(String categoryId) {
            Product product = new Product(categoryId, null, ean, manufacturerCode, brand, label, name, pricingGroup);
            if (availabilityType != null) {
                product.setAvailabilityType(availabilityType);
            }
            return product;
        }
    }

    private List<Row> products = new ArrayList<>();

    public static ProductsBulkAddForm of(List<Product> products) {
        ProductsBulkAddForm form = new ProductsBulkAddForm();
        form.setProducts(products.stream().map(Row::new).collect(Collectors.toCollection(ArrayList::new)));
        return form;
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
            Row row = products.get(index);
            if (StringUtils.isBlank(row.getName())) {
                errors.put(fieldId(index, "name"), "product.error.name.required");
            }
            // The review edits the identifiers, so it checks them by the same rules as the product page. A row with
            // neither of them is a mistake of both fields -- either one fixes it -- so both are marked and the error
            // summary links to whichever the operator wants to fill in.
            String identifierError = ProductForm.identifierError(row.getEan(), row.getManufacturerCode());
            if (identifierError != null) {
                errors.put(fieldId(index, "ean"), identifierError);
            }
            if (ProductForm.IDENTIFIER_REQUIRED.equals(identifierError)) {
                errors.put(fieldId(index, "manufacturerCode"), identifierError);
            }
            if (!categoryLabels.isEmpty() && !categoryLabels.contains(row.getLabel())) {
                errors.put(fieldId(index, "label"), "product.error.label.notInList");
            }
            if (pricingGroups.stream().noneMatch(group -> group.equalsIgnoreCase(row.getPricingGroup()))) {
                errors.put(fieldId(index, "pricingGroup"), "product.error.group.unknown");
            }
        }
        return errors;
    }
}
