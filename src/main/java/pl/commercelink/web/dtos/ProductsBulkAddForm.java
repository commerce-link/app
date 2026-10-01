package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.Product;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
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
        /**
         * Text, not the enum: the review carries it in a hidden field, and a value no product can have is a mistake
         * of the field (422 with the review) rather than a binding failure answered with a bare 400.
         */
        private String availabilityType;

        public Row() {
        }

        private Row(Product product) {
            this.ean = product.getEan();
            this.manufacturerCode = product.getManufacturerCode();
            this.brand = product.getBrand();
            this.name = product.getName();
            this.label = product.getLabel();
            this.pricingGroup = product.getPricingGroup();
            this.availabilityType = product.getAvailabilityType() == null ? null : product.getAvailabilityType().name();
        }

        /**
         * The product as it is created; the PIM entry is resolved by the controller, never taken from the row. The
         * identifiers go through the setters, trimmed, as the product page saves them: the constructor stores them as
         * posted, and an EAN with spaces around it would never match its PIM entry or the inventory again.
         */
        public Product toProduct(String categoryId) {
            Product product = new Product(categoryId, null, null, null, brand, label, name, pricingGroup);
            product.setEan(StringUtils.trimToNull(ean));
            product.setManufacturerCode(StringUtils.trimToNull(manufacturerCode));
            if (StringUtils.isNotBlank(availabilityType)) {
                ProductForm.availabilityOf(availabilityType).ifPresent(product::setAvailabilityType);
            }
            return product;
        }
    }

    /** The key of a line of the error summary: "Produkt {0}: {1}", the number of the row and the message of its field. */
    public static final String SUMMARY_LINE = "catalog.products.review.error.n";

    private static final Pattern ROW_FIELD = Pattern.compile("product-(\\d+)-.+");

    private List<Row> products = new ArrayList<>();
    /**
     * Given when the review is shown and posted back in a hidden field (RF-6). Every row is saved under an id derived
     * from it, so the same review sent twice -- two tabs, a double click, Back and send -- writes each row under the
     * same id both times, and the second write, a conditional put (a new product carries no version), finds the first.
     */
    private String reviewId;

    public static ProductsBulkAddForm of(List<Product> products) {
        ProductsBulkAddForm form = new ProductsBulkAddForm();
        form.setProducts(products.stream().map(Row::new).collect(Collectors.toCollection(ArrayList::new)));
        form.setReviewId(UUID.randomUUID().toString());
        return form;
    }

    /**
     * The product row {@code index} creates, under the id this review gives it: derived from the review, the category,
     * the row and the identifiers the row is saved with, so a row corrected before the review was sent again is
     * another product. A review without an id of the generator's shape (a page opened before the id travelled with
     * it, a forged value) saves under fresh ids as it always did; any id only ever creates -- the save never
     * overwrites an existing product -- so a chosen one can at most make its own row look saved already.
     */
    public Product toProduct(int index, String categoryId) {
        Product product = products.get(index).toProduct(categoryId);
        if (hasWellFormedReviewId()) {
            String seed = String.join("\n", categoryId, reviewId, String.valueOf(index),
                    Objects.toString(product.getEan(), ""), Objects.toString(product.getManufacturerCode(), ""));
            product.setProductId(UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString());
        }
        return product;
    }

    private boolean hasWellFormedReviewId() {
        if (reviewId == null) {
            return false;
        }
        try {
            return UUID.fromString(reviewId).toString().equals(reviewId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** The id of a field of the row at {@code index}; an error is keyed by it, so the summary links to the field. */
    public static String fieldId(int index, String field) {
        return "product-" + index + "-" + field;
    }

    /**
     * The error summary of the review names the row of every error ("Produkt 2: Podaj nazwę produktu."), because the
     * same message repeats row after row; the message at the field sits in its row and stays as it is.
     *
     * @param texts field id to the message shown at the field
     * @param line  formats a summary line from the number of the row (from 1) and the message, see {@link #SUMMARY_LINE}
     */
    public static Map<String, String> summary(Map<String, String> texts, BiFunction<String, String, String> line) {
        return FormRules.numberedSummary(texts, ROW_FIELD, line);
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
            // The review edits the identifiers, so it checks them by the same rules as the product page: both are
            // required, each missing one an error of its own field that the summary links to.
            ProductForm.validateIdentifiers(errors, fieldId(index, "ean"), fieldId(index, "manufacturerCode"),
                    row.getEan(), row.getManufacturerCode());
            if (!categoryLabels.isEmpty() && !categoryLabels.contains(row.getLabel())) {
                errors.put(fieldId(index, "label"), "product.error.label.notInList");
            }
            if (pricingGroups.stream().noneMatch(group -> group.equalsIgnoreCase(row.getPricingGroup()))) {
                errors.put(fieldId(index, "pricingGroup"), "product.error.group.unknown");
            }
            // Only a forged request carries another value: the review posts back the one the proposal was given.
            if (StringUtils.isNotBlank(row.getAvailabilityType()) && ProductForm.availabilityOf(row.getAvailabilityType()).isEmpty()) {
                errors.put(fieldId(index, "availabilityType"), "product.error.availability.invalid");
            }
        }
        return errors;
    }
}
