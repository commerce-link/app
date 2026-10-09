package pl.commercelink.web.orders;

import java.util.List;

/** Everything the orders archive prints, already resolved (spec §3). */
public record OrderArchivePage(OrderArchiveQuery query, List<SegmentLink> segments, DateMenu dates, List<Chip> chips,
                               String resultsLine, List<OrderRow> rows, ArchivePager pagination, EmptyState emptyState,
                               OpenNotice openNotice, SearchScope scope) {

    public record SegmentLink(String label, String href, boolean active) { }
    public record DateMenu(String value, List<Preset> presets, String from, String to) { }
    public record Preset(String label, String href, boolean active) { }
    public record Chip(String label, String clearHref, String clearLabel) { }
    public record Action(String label, String href) { }
    public record EmptyState(String text, List<Action> actions) { }
    /** The typed number belongs to an open order: say so and lead there instead of "no results". */
    public record OpenNotice(String text, String actionLabel, String actionHref) { }
    /** A text search stopped after its batch: how far back it looked and the link that searches further. */
    public record SearchScope(String text, String moreLabel, String moreHref) { }

    public String fragmentPath() {
        return OrderArchiveQuery.FRAGMENT_PATH;
    }
}
