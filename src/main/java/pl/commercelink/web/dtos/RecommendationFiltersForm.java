package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.InventoryDefinition;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.web.catalog.InventoryFilterLabels;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The recommendation filters of a category: one repeatable group per filter, with the fields of the chosen kind. Every
 * kind posts the same field names, so the page can switch between them without renaming anything; which of them counts
 * is decided by the kind.
 */
@Getter
@Setter
public class RecommendationFiltersForm {

    @Getter
    @Setter
    public static class FilterForm {

        private String type;
        private String values;
        private String minPrice;
        private String maxPrice;
        private String brandLines;
        /** Metadata of the saved filter that the form has no field for; posted back so nothing is lost on a save. */
        private List<String> unknownKeys = new ArrayList<>();
        private List<String> unknownValues = new ArrayList<>();

        public Optional<InventoryFilterType> parsedType() {
            try {
                return Optional.of(InventoryFilterType.valueOf(StringUtils.defaultString(type)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    private List<FilterForm> filters = new ArrayList<>();

    public static RecommendationFiltersForm from(CategoryDefinition category) {
        RecommendationFiltersForm form = new RecommendationFiltersForm();
        form.filters = category.getInventoryDefinitions().stream()
                .filter(definition -> definition.getType() != null)
                .map(InventoryFilterLabels::fromDefinition)
                .collect(Collectors.toCollection(ArrayList::new));
        return form;
    }

    /** The id of a field of one filter: also its error key, so the error summary can link to the field. */
    public static String fieldId(int index, String field) {
        return "filter-" + index + "-" + field;
    }

    public Map<String, String> validate() {
        Map<String, String> errors = new LinkedHashMap<>();
        for (int index = 0; index < filters.size(); index++) {
            FilterForm filter = filters.get(index);
            Optional<InventoryFilterType> type = filter.parsedType();
            if (type.isEmpty()) {
                errors.put(fieldId(index, "type"), "catalog.filter.type.invalid");
                continue;
            }
            switch (InventoryFilterLabels.kindOf(type.get())) {
                case LIST -> FormRules.requireText(errors, fieldId(index, "values"), filter.values, "catalog.filter.values.required");
                case PRICE_RANGE -> validatePrices(errors, index, filter);
                case BY_BRAND -> validateBrandLines(errors, index, filter);
            }
        }
        return errors;
    }

    private static void validatePrices(Map<String, String> errors, int index, FilterForm filter) {
        Optional<Integer> min = bound(filter.minPrice);
        Optional<Integer> max = bound(filter.maxPrice);
        if (StringUtils.isNotBlank(filter.minPrice) && min.isEmpty()) {
            errors.put(fieldId(index, "minPrice"), "catalog.filter.price.invalid");
        }
        if (StringUtils.isNotBlank(filter.maxPrice) && max.isEmpty()) {
            errors.put(fieldId(index, "maxPrice"), "catalog.filter.price.invalid");
        }
        if (StringUtils.isBlank(filter.minPrice) && StringUtils.isBlank(filter.maxPrice)) {
            errors.put(fieldId(index, "minPrice"), "catalog.filter.price.required");
        }
        if (min.isPresent() && max.isPresent() && max.get() < min.get()) {
            errors.put(fieldId(index, "maxPrice"), "catalog.filter.price.order");
        }
    }

    private static void validateBrandLines(Map<String, String> errors, int index, FilterForm filter) {
        if (StringUtils.isBlank(filter.brandLines)) {
            errors.put(fieldId(index, "brandLines"), "catalog.filter.brandLines.required");
            return;
        }
        try {
            InventoryFilterLabels.parseBrandLines(filter.brandLines);
        } catch (InventoryFilterLabels.BrandLineException e) {
            // The line number belongs to the message, not to the key; the controller translates it before rendering.
            errors.put(fieldId(index, "brandLines"), "catalog.filter.brandLines.line:" + e.lineNumber());
        }
    }

    private static Optional<Integer> bound(String value) {
        return StringUtils.isBlank(value) ? Optional.empty() : FormNumbers.integer(value).filter(amount -> amount >= 0);
    }

    public List<InventoryDefinition> toDefinitions() {
        return filters.stream()
                .map(filter -> new InventoryDefinition(filter.parsedType().orElseThrow(), InventoryFilterLabels.toMetadata(filter)))
                .toList();
    }
}
