package pl.commercelink.inventory.search;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyEan;
import static pl.commercelink.taxonomy.UnifiedProductIdentifiers.unifyMfn;

/**
 * The EAN and manufacturer code a search result is anchored to: the searched value itself, otherwise the value most
 * offers carry. Rows are compared against it, and the product header shows its display form.
 */
record ProductCodes(String ean, String code) {

    static ProductCodes resolve(MatchedBy matchedBy, String query, Collection<String> rowEans, Collection<String> rowCodes) {
        String ean = matchedBy == MatchedBy.EAN ? displayOf(query, rowEans, ProductCodes::unifiedEanOf) : mostCommon(rowEans, ProductCodes::unifiedEanOf);
        String code = matchedBy == MatchedBy.MFN ? displayOf(query, rowCodes, ProductCodes::unifiedCodeOf) : mostCommon(rowCodes, ProductCodes::unifiedCodeOf);
        return new ProductCodes(ean, code);
    }

    String unifiedEan() {
        return unifiedEanOf(ean);
    }

    String unifiedCode() {
        return unifiedCodeOf(code);
    }

    ProductCodes orElse(String fallbackEan, String fallbackCode) {
        return new ProductCodes(ean != null ? ean : CodeMatch.blankToNull(fallbackEan), code != null ? code : CodeMatch.blankToNull(fallbackCode));
    }

    private static String unifiedEanOf(String value) {
        return unifyEan(CodeMatch.blankToNull(value));
    }

    private static String unifiedCodeOf(String value) {
        return unifyMfn(CodeMatch.blankToNull(value));
    }

    // prefer the spelling a supplier actually uses over the normalized query (unifyEan drops leading zeros)
    private static String displayOf(String query, Collection<String> rowValues, Function<String, String> unify) {
        String unifiedQuery = unify.apply(query == null ? null : query.trim());
        return rowValues.stream()
                .filter(value -> unifiedQuery != null && unifiedQuery.equals(unify.apply(value)))
                .findFirst()
                .orElse(unifiedQuery == null ? null : query.trim());
    }

    // ties resolve to the alphabetically first normalized value so the header stays the same between searches
    private static String mostCommon(Collection<String> values, Function<String, String> unify) {
        Map<String, Long> counts = values.stream()
                .map(CodeMatch::blankToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(unify, Collectors.counting()));
        return counts.entrySet().stream()
                .min(Comparator.<Map.Entry<String, Long>>comparingLong(entry -> -entry.getValue()).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .flatMap(unified -> values.stream().filter(value -> unified.equals(unify.apply(value))).findFirst())
                .orElse(null);
    }
}
