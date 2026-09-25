package pl.commercelink.web.catalog;

import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.CategoryDefinitionType;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Everything the category page needs to draw the toolbar counts and the table. */
public record CategoryPageModel(List<ProductRow> rows, Map<ProductStatus, Integer> statusCounts,
                                List<LabelOption> labelOptions, Map<String, Integer> featureCounts,
                                boolean dynamic, boolean hasMapping) {

    public static final List<String> FEATURES = List.of("marketplace", "stock", "srp", "mrp", "service");

    public record LabelOption(String value, int count) {
    }

    public static CategoryPageModel of(List<ProductRow> rows, CategoryDefinition category) {
        Map<ProductStatus, Integer> statuses = new EnumMap<>(ProductStatus.class);
        for (ProductStatus status : ProductStatus.values()) {
            statuses.put(status, (int) rows.stream().filter(row -> row.status() == status).count());
        }
        // The category's own order first, so the list reads the same as on the Basics page; a label that fell out of it
        // is still worth filtering by, and lands at the end.
        LinkedHashMap<String, Integer> labels = new LinkedHashMap<>();
        category.getGroupingOrder().forEach(label -> labels.put(label, 0));
        rows.stream().map(ProductRow::label).filter(Objects::nonNull).forEach(label -> labels.merge(label, 1, Integer::sum));
        Map<String, Integer> features = new LinkedHashMap<>();
        FEATURES.forEach(feature -> features.put(feature, (int) rows.stream().filter(row -> row.features().contains(feature)).count()));
        return new CategoryPageModel(rows, statuses,
                labels.entrySet().stream().map(entry -> new LabelOption(entry.getKey(), entry.getValue())).toList(),
                features, category.hasType(CategoryDefinitionType.Dynamic), category.hasCategoryMapping());
    }

    public int total() {
        return rows.size();
    }

    public boolean hasLabels() {
        return !labelOptions.isEmpty();
    }
}
