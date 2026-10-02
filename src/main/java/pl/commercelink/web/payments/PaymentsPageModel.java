package pl.commercelink.web.payments;

import java.util.List;

/** Everything the Payments template renders, texts resolved (spec §3–§6). */
public record PaymentsPageModel(PaymentsQuery query, PaymentSide side, List<Tile> tiles, List<SideTab> tabs,
                                String menuKey, String menuParam, String menuSummary, List<Option> menuOptions,
                                String searchPlaceholder, List<Chip> chips, String resultsCount, String resultsAmount,
                                String resultsTail, List<PayableRow> payables, List<ReceivableRow> receivables,
                                EmptyState emptyState, boolean invoicingConnected, String returnTo) {

    /** {@code tone} is a cl-stat tone class, empty when the tile reads in plain ink. */
    public record Tile(String label, long count, String payablesHint, String receivablesHint, String href, boolean active,
                       String tone) { }
    public record SideTab(String label, long count, String href, boolean active) { }
    public record Option(String value, String label, long count, boolean selected) { }
    public record Chip(String label, String clearHref, String clearLabel) { }
    public record EmptyState(String text, String actionLabel, String actionHref) { }

    public boolean payablesShown() {
        return side == PaymentSide.PAYABLES;
    }
}
