package pl.commercelink.warehouse.builtin;

import pl.commercelink.web.orders.Pagination;

import java.util.List;
import java.util.Map;

/** Everything the warehouse list renders, texts resolved (spec §4). */
public record WarehousePageModel(WarehouseListQuery query, boolean admin, boolean wms, List<Tile> tiles,
                                 List<Option> statusOptions, String statusSummary, List<Option> categoryOptions,
                                 String categorySummary, List<Chip> chips, Results results,
                                 Map<WarehouseListQuery.Sort, SortHeader> sortHeaders, List<WarehouseItemRow> rows,
                                 Pagination pagination, EmptyState emptyState, boolean storeEmpty, int activeFilterCount,
                                 long destroyedCount, List<BulkActionView> menuActions, BulkActionView destroyAction,
                                 List<Option> destroyReasons) {

    public record Tile(String label, String value, String hint, String href, boolean active) { }
    public record Option(String value, String label, long count, boolean selected, String toggleHref) { }
    /**
     * The results line in parts so the template can set the amounts as cl-results-amount (Payments): count is the text
     * up to the net amount; with nothing listed it is the bare count and the other parts are null.
     */
    public record Results(String count, String net, String grossLabel, String gross) { }
    public record Chip(String label, String clearHref, String clearLabel) { }
    public record SortHeader(String href, String ariaSort) { }
    public record EmptyState(String text, String actionLabel, String actionHref) { }
    public record BulkActionView(String key, String path, String label, String forStatuses, boolean needsQuantity,
                                 boolean confirm, boolean sameSource, boolean danger, String statusReason,
                                 String confirmTitle, String confirmMessage, String confirmAction, String dialogTitle) { }
}
