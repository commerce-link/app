package pl.commercelink.web.rma;

import pl.commercelink.web.orders.ArchivePager;
import pl.commercelink.web.orders.OrderArchivePage.Chip;
import pl.commercelink.web.orders.OrderArchivePage.DateMenu;
import pl.commercelink.web.orders.OrderArchivePage.EmptyState;
import pl.commercelink.web.orders.OrderArchivePage.SearchScope;

import java.util.List;

/** Everything the RMA list prints, already resolved (spec §3). */
public record RmaListPage(RmaListQuery query, List<Tile> tiles, List<SegmentLink> segments, StatusMenu statusMenu,
                          DateMenu dates, List<Chip> chips, String resultsLine, List<RmaRow> rows,
                          ArchivePager pagination, EmptyState emptyState, SearchScope scope) {

    public record Tile(String label, String hint, long count, String href, boolean active) { }
    public record SegmentLink(String label, Long count, String href, boolean active) { }
    /** The option shape fragments/deliveries-list :: checks reads; count is null where counting is not offered. */
    public record StatusOption(String value, String label, Long count, boolean selected) { }
    public record StatusMenu(String value, List<StatusOption> options) { }

    public String fragmentPath() {
        return RmaListQuery.FRAGMENT_PATH;
    }
}
