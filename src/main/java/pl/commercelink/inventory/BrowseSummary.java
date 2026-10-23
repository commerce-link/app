package pl.commercelink.inventory;

import java.util.Map;

/** Products a store can browse, counted per PIM category id (leaves and {@link BrowseIndex#UNASSIGNED}) and per supplier. */
public record BrowseSummary(Map<String, Integer> byCategory, Map<String, Integer> bySupplier, int total) {

    public static final BrowseSummary EMPTY = new BrowseSummary(Map.of(), Map.of(), 0);
}
