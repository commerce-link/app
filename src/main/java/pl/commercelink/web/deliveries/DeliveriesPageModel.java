package pl.commercelink.web.deliveries;

import pl.commercelink.web.orders.Pagination;

import java.util.List;
import java.util.Map;

/** Everything the deliveries list template renders, texts resolved (spec §3). */
public record DeliveriesPageModel(DeliveryListQuery query, boolean superAdmin, boolean canCreate, List<Tile> tiles,
                                  List<ScopeOption> scopes, List<Option> stateTransitOptions,
                                  List<Option> stateReceivedOptions, String stateSummary,
                                  List<Option> providerOptions, String providerSummary, List<Option> settleOptions,
                                  String settleSummary, DateMenu dates, List<Chip> chips, String resultsLine,
                                  Map<DeliveryListQuery.Sort, SortHeader> sortHeaders, List<DeliveryRow> rows,
                                  Pagination pagination, EmptyState emptyState, int activeFilterCount) {

    public record Tile(String label, long count, String hint, String href, boolean active) { }
    public record ScopeOption(String label, Long count, String href, boolean active) { }
    public record Option(String value, String label, long count, boolean selected, String toggleHref) { }
    public record DateMenu(String key, String value, String from, String to, String allHistoryHref, boolean historyWindow) { }
    public record Chip(String label, String clearHref, String clearLabel) { }
    public record SortHeader(String href, String ariaSort) { }
    public record EmptyState(String text, String actionLabel, String actionHref) { }
}
