package pl.commercelink.web.catalog;

import org.apache.commons.lang3.StringUtils;
import pl.commercelink.products.InventoryDefinition;
import pl.commercelink.products.filters.InventoryFilterType;
import pl.commercelink.starter.dynamodb.Metadata;
import pl.commercelink.web.dtos.FormNumbers;
import pl.commercelink.web.dtos.RecommendationFiltersForm.FilterForm;
import pl.commercelink.web.dtos.RecommendationFiltersForm.MetadataForm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static pl.commercelink.products.filters.InventoryFilterType.BRAND_NAME;
import static pl.commercelink.products.filters.InventoryFilterType.EAN_NOT_EQ;
import static pl.commercelink.products.filters.InventoryFilterType.PRICE_RANGE;
import static pl.commercelink.products.filters.InventoryFilterType.PRODUCT_EAN_BY_BRAND_NOT_EQ;
import static pl.commercelink.products.filters.InventoryFilterType.PRODUCT_LINE_BY_BRAND;
import static pl.commercelink.products.filters.InventoryFilterType.PRODUCT_LINE_BY_BRAND_NOT_CONTAIN;
import static pl.commercelink.products.filters.InventoryFilterType.PRODUCT_TITLE_CONTAINS;
import static pl.commercelink.products.filters.InventoryFilterType.PRODUCT_TITLE_DOES_NOT_CONTAIN;

/**
 * How the recommendation filter form maps onto InventoryDefinition metadata. The keys are the ones the filter classes
 * read (Brands, MinPrice/MaxPrice, Keywords, Eans); the "by brand" filters use the brand name as the key, which the form
 * shows as one "Brand: value, value" line per brand. Metadata the form does not understand becomes an editable key/value
 * row, so a filter written by hand can be corrected or cleaned up instead of travelling along unseen.
 */
public final class InventoryFilterLabels {

    public enum Kind { LIST, PRICE_RANGE, BY_BRAND }

    /** What PriceRangeInventoryFilter reads as "no upper bound"; the form shows it as an empty field. */
    public static final String NO_MAX_PRICE = String.valueOf(Integer.MAX_VALUE);

    private static final String NO_MIN_PRICE = "0";
    private static final String MIN_PRICE = "MinPrice";
    private static final String MAX_PRICE = "MaxPrice";

    /** A brand line without a colon cannot be split into a brand and its values; the form names the line. */
    public static class BrandLineException extends RuntimeException {

        private final int lineNumber;

        public BrandLineException(int lineNumber) {
            super("Brand line " + lineNumber + " has no colon");
            this.lineNumber = lineNumber;
        }

        public int lineNumber() {
            return lineNumber;
        }
    }

    private static final Map<InventoryFilterType, String> LIST_KEYS = Map.of(
            BRAND_NAME, "Brands",
            PRODUCT_TITLE_CONTAINS, "Keywords",
            PRODUCT_TITLE_DOES_NOT_CONTAIN, "Keywords",
            EAN_NOT_EQ, "Eans");

    /** The order of the choice: the filters on one value first, the price range, then the ones written per brand. */
    private static final List<InventoryFilterType> ORDER = List.of(BRAND_NAME, PRICE_RANGE, PRODUCT_TITLE_CONTAINS,
            PRODUCT_TITLE_DOES_NOT_CONTAIN, EAN_NOT_EQ, PRODUCT_LINE_BY_BRAND, PRODUCT_LINE_BY_BRAND_NOT_CONTAIN,
            PRODUCT_EAN_BY_BRAND_NOT_EQ);

    private InventoryFilterLabels() {
    }

    public static Kind kindOf(InventoryFilterType type) {
        if (type == PRICE_RANGE) {
            return Kind.PRICE_RANGE;
        }
        return LIST_KEYS.containsKey(type) ? Kind.LIST : Kind.BY_BRAND;
    }

    public static String labelKey(InventoryFilterType type) {
        return "catalog.filter.type." + type.name();
    }

    public static String helpKey(InventoryFilterType type) {
        return labelKey(type) + ".help";
    }

    /** Label of the field the filter is written in ("Brands", "Words"); the price range names its two fields itself. */
    public static String fieldKey(InventoryFilterType type) {
        return labelKey(type) + ".field";
    }

    /** The kinds of filter the page offers, in the order of the choice, with the message keys of each one. */
    public static List<Map<String, String>> options() {
        return ORDER.stream().map(type -> Map.of(
                "value", type.name(),
                "kind", kindOf(type).name(),
                "labelKey", labelKey(type),
                "helpKey", helpKey(type),
                "fieldKey", fieldKey(type))).toList();
    }

    /**
     * Whose texts the field shared by the filters of one kind shows: the chosen filter's, or — when the filter chosen is
     * of another kind — the first type of the kind, so the shared field is never left without a label.
     */
    public static String shownType(String kind, String selectedType) {
        List<String> ofKind = ORDER.stream().filter(type -> kindOf(type).name().equals(kind))
                .map(InventoryFilterType::name).toList();
        return ofKind.contains(selectedType) ? selectedType : ofKind.get(0);
    }

    /** The values of a kind as variant-fields.js reads them: "A|B|C". */
    public static String variants(Kind kind) {
        return String.join("|", ORDER.stream().filter(type -> kindOf(type) == kind).map(InventoryFilterType::name).toList());
    }

    public static FilterForm fromDefinition(InventoryDefinition definition) {
        FilterForm form = new FilterForm();
        form.setType(definition.getType().name());
        List<Metadata> metadata = definition.getMetadata() == null ? List.of() : definition.getMetadata();
        switch (kindOf(definition.getType())) {
            case LIST -> {
                String key = LIST_KEYS.get(definition.getType());
                metadata.forEach(entry -> {
                    if (key.equalsIgnoreCase(entry.getKey())) {
                        form.setValues(entry.getValue());
                    } else {
                        keepUnknown(form, entry);
                    }
                });
            }
            case PRICE_RANGE -> metadata.forEach(entry -> {
                if (MIN_PRICE.equalsIgnoreCase(entry.getKey())) {
                    form.setMinPrice(NO_MIN_PRICE.equals(entry.getValue()) ? "" : entry.getValue());
                } else if (MAX_PRICE.equalsIgnoreCase(entry.getKey())) {
                    form.setMaxPrice(NO_MAX_PRICE.equals(entry.getValue()) ? "" : entry.getValue());
                } else {
                    keepUnknown(form, entry);
                }
            });
            case BY_BRAND -> form.setBrandLines(brandLines(metadata));
        }
        return form;
    }

    public static List<Metadata> toMetadata(FilterForm form) {
        InventoryFilterType type = InventoryFilterType.valueOf(form.getType());
        List<Metadata> metadata = new ArrayList<>();
        switch (kindOf(type)) {
            case LIST -> metadata.add(new Metadata(LIST_KEYS.get(type), StringUtils.trim(form.getValues())));
            // Both bounds are written even when only one was typed: the filter runs only with each of its keys present.
            case PRICE_RANGE -> {
                metadata.add(new Metadata(MIN_PRICE, bound(form.getMinPrice(), NO_MIN_PRICE)));
                metadata.add(new Metadata(MAX_PRICE, bound(form.getMaxPrice(), NO_MAX_PRICE)));
            }
            case BY_BRAND -> parseBrandLines(form.getBrandLines())
                    .forEach((brand, values) -> metadata.add(new Metadata(brand, values)));
        }
        // A row of the page whose key came back empty is a removed row, and a gap in the posted indexes binds as null.
        form.getUnknown().stream()
                .filter(pair -> pair != null && StringUtils.isNotBlank(pair.getKey()))
                .forEach(pair -> metadata.add(new Metadata(pair.getKey().trim(), StringUtils.defaultString(pair.getValue()))));
        return metadata;
    }

    /**
     * The number as the filter reads it, not as it was typed: PriceRangeInventoryFilter parses the value with
     * Integer.parseInt, and a group separator ("5 000", which the panel itself prints) would make it throw — and a
     * throwing filter silently drops every product instead of saying anything.
     */
    private static String bound(String typed, String noBound) {
        return FormNumbers.integer(typed).map(String::valueOf).orElse(noBound);
    }

    /** One "Brand: value, value" line per brand; empty lines are skipped, a line without a colon is an error with its number. */
    public static LinkedHashMap<String, String> parseBrandLines(String text) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String[] lines = StringUtils.defaultString(text).split("\\r?\\n");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon <= 0 || colon == line.length() - 1) {
                throw new BrandLineException(index + 1);
            }
            result.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }
        return result;
    }

    public static String brandLines(List<Metadata> metadata) {
        return metadata.stream().map(entry -> entry.getKey() + ": " + entry.getValue()).reduce((a, b) -> a + "\n" + b).orElse("");
    }

    private static void keepUnknown(FilterForm form, Metadata entry) {
        MetadataForm pair = new MetadataForm();
        pair.setKey(entry.getKey());
        pair.setValue(entry.getValue());
        form.getUnknown().add(pair);
    }
}
