package pl.commercelink.inventory;

import java.util.Map;

/**
 * Counts of one browse selection: products per PIM category id among those the list shows, and products per supplier
 * among the selection's categories and phrase, whichever suppliers the list is filtered to — the supplier menu offers
 * the others too.
 */
public record BrowseFacets(Map<String, Integer> byCategory, Map<String, Integer> bySupplier) {

    public static final BrowseFacets EMPTY = new BrowseFacets(Map.of(), Map.of());
}
