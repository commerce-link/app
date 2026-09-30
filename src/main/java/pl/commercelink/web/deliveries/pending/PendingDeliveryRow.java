package pl.commercelink.web.deliveries.pending;

import pl.commercelink.web.deliveries.pending.PendingDeliveriesQuery.Kind;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * One row of the pending deliveries page (spec §3.2, §3.3): a supplier's warehouse delivery or an order's dropship,
 * texts resolved. {@code due}, {@code cost}, {@code orderIds} and {@code searchText} serve the filters and are not shown.
 */
public record PendingDeliveryRow(Kind kind, String key, String keyHref, String customer, String provider,
                                 String providerLabel, String sourceText, boolean forwardToCustomer, int pieces,
                                 String piecesText, LocalDate due, String dueNote, String dueTone, boolean api,
                                 boolean approval, String modeText, double cost, String costText, String createHref,
                                 String detailId, List<Item> items, List<String> orderIds, String searchText) {

    public record Item(String name, String codes, String qtyText, String unitText, String valueText, List<Source> sources) {
    }

    /** Where an item's quantity comes from: an order (link) or the warehouse restock (no link for the super admin). */
    public record Source(String label, String href, int qty, boolean warehouse) {
    }

    /** What names the row in a sentence (button labels): the supplier of a warehouse row, the order of a dropship. */
    public String label() {
        return kind == Kind.WAREHOUSE ? providerLabel : key;
    }

    /** Order number from its start, anything else (customer, product, codes, supplier) anywhere; case-insensitive. */
    public boolean matches(String q) {
        if (q == null) {
            return true;
        }
        String needle = q.toLowerCase(Locale.ROOT);
        return orderIds.stream().anyMatch(id -> id.toLowerCase(Locale.ROOT).startsWith(needle)) || searchText.contains(needle);
    }
}
