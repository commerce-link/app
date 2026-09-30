package pl.commercelink.web.deliveries.pending;

import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.util.List;

/** Everything the pending deliveries template renders, texts resolved (spec §3). */
public record PendingDeliveriesPageModel(PendingDeliveriesQuery query, boolean superAdmin, String listPath,
                                         String fragmentPath, String backHref, List<Tile> tiles, List<KindTab> tabs,
                                         Kind activeKind, String activeTabLabel, String tabDescription,
                                         List<Option> providerOptions, String providerSummary, List<Chip> chips,
                                         String clearHref, String searchClearHref, String resultsLine,
                                         List<PendingDeliveryRow> rows, EmptyState emptyState, boolean nothingPending,
                                         int activeFilterCount) {

    public boolean dropship() {
        return activeKind == Kind.DROPSHIP;
    }

    /** A tile without href only counts (approval, cost); one with href narrows the page to what it counts. */
    public record Tile(String label, String value, String hint, String href, boolean active) { }
    public record KindTab(String label, long count, String href, boolean active) { }
    public record Option(String value, String label, long count, boolean selected, String toggleHref) { }
    public record Chip(String label, String clearHref, String clearLabel) { }
    /** inline = a sentence in place of the tab's table; otherwise the page-wide empty block with an action. */
    public record EmptyState(String text, String actionLabel, String actionHref, boolean inline) { }
}
