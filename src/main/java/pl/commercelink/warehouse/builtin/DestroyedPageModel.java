package pl.commercelink.warehouse.builtin;

import pl.commercelink.web.orders.Pagination;

import java.util.List;
import java.util.Map;

/**
 * The destroyed archive as the template prints it (spec §6): tiles, filter summaries, chips, sort headers and the rows of
 * one page. Public because the template engine and tests outside the package read it.
 */
public record DestroyedPageModel(DestroyedListQuery query, List<WarehousePageModel.Tile> tiles, String dateSummary,
                                 String fromValue, String toValue, List<WarehousePageModel.Option> categoryOptions,
                                 String categorySummary, List<WarehousePageModel.Option> conditionOptions, String conditionSummary,
                                 List<WarehousePageModel.Chip> chips, String results,
                                 Map<DestroyedListQuery.Sort, WarehousePageModel.SortHeader> sortHeaders, List<Row> rows,
                                 Pagination pagination, WarehousePageModel.EmptyState emptyState, boolean storeEmpty,
                                 int activeFilterCount) {

    /** One archive row: the warehouse row, the destruction day and the row menu links (null when not offered). */
    public record Row(WarehouseItemRow item, String destroyedOn, String documentsHref, String historyHref, String menuLabel) { }
}
