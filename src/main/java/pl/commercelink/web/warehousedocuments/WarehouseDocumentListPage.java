package pl.commercelink.web.warehousedocuments;

import pl.commercelink.web.orders.Pagination;

import java.util.List;

/** The warehouse documents list, every text resolved (spec §2). */
public record WarehouseDocumentListPage(WarehouseDocumentListQuery query, boolean superAdmin, boolean documentsEnabled,
        String fragmentPath, List<SegmentLink> segments, List<Option> reasonOptions, String reasonSummary,
        DateMenu dates, List<Chip> chips, String resultsLine, List<DocumentRow> rows, Pagination pagination,
        EmptyState emptyState, String settingsHref) {
    public record SegmentLink(String label, String title, String href, boolean active) { }
    public record Option(String value, String label, boolean selected) { }
    public record DateMenu(String value, String from, String to) { }
    public record Chip(String label, String clearHref, String clearLabel) { }
    public record EmptyState(String text, String actionLabel, String actionHref) { }
    public record DocumentRow(String href, String number, String typeName, boolean incoming, String reason, String note,
                              String source, String counterparty, String date, String timeAndAuthor, String storeId) { }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
