package pl.commercelink.web.fulfilment;

import pl.commercelink.orders.fulfilment.FulfilmentGroup;

import java.util.List;
import java.util.Objects;

/**
 * The supplier selection page (spec docs/active/fulfilment-queue-redesign/2026-10-08-fulfilment-select-design.md):
 * everything the template shows except the offer data and the posted fields, which stay on FulfilmentForm. The page
 * serves two entries: the fulfilment queue's orders (ORDERS) and the warehouse restock (RESTOCK, no orders, no
 * coverage, profit unknown without a budget). Form actions, order links and the way back are computed here, so the
 * browser never builds an address from hidden fields.
 */
public record FulfilmentSelectPage(Mode mode, Kind kind, String storeName, String context, String strategyKey,
                                   List<String> narrowingKeys, Step step, String backHref, Actions actions,
                                   List<OrderRef> orders, List<Category> categories, List<SupplierRow> suppliers,
                                   List<SupplierTotal> committed, List<VariantOption> variants, List<Missing> missing,
                                   boolean profitKnown, int itemsTotal, EmptyState emptyState, String noMatchKey,
                                   boolean multiOnSingleOrder, String commitHelpKey) {

    public boolean isOrders() {
        return mode == Mode.ORDERS;
    }

    public boolean hasOffers() {
        return emptyState == EmptyState.NONE;
    }

    /** The order chips: only when several orders share the page (one order is named in the header). */
    public boolean showCoverage() {
        return isOrders() && orders.size() > 1;
    }

    /** The order references under each offer: the same rule as the chips. */
    public boolean showOrderRefs() {
        return showCoverage();
    }

    /** Items are waiting but no offer survived: the table holds only the "Bez oferty" group. */
    public boolean noOfferMatched() {
        return categories.isEmpty() && !missing.isEmpty();
    }

    public boolean noVariantApplied() {
        return variants.stream().noneMatch(VariantOption::applied);
    }

    /** The order of an allocation; an id the page does not know (restock allocations have none) gets a bare reference. */
    public OrderRef order(String orderId) {
        return orders.stream().filter(o -> Objects.equals(o.orderId(), orderId)).findFirst()
                .orElse(new OrderRef(orderId, "", null, 0));
    }

    public String titleKey() {
        return isOrders() ? "fulfilment.select.title" : "fulfilment.select.title.restock";
    }

    public String leadKey() {
        return isOrders() ? "fulfilment.select.lead" : "fulfilment.select.lead.restock";
    }

    public String backKey() {
        return isOrders() ? "fulfilment.select.back" : "fulfilment.select.back.restock";
    }

    public enum Mode {
        ORDERS,
        RESTOCK
    }

    /** What the page is about, as the header pill shows it. */
    public enum Kind {
        WAREHOUSE("fulfilment.select.kind.warehouse", "fa-warehouse"),
        DROPSHIP("fulfilment.select.kind.dropship", "fa-truck"),
        MIXED("fulfilment.select.kind.mixed", "fa-list-alt"),
        RESTOCK("fulfilment.select.kind.restock", "fa-boxes");

        private final String key;
        private final String icon;

        Kind(String key, String icon) {
            this.key = key;
            this.icon = icon;
        }

        public String key() {
            return key;
        }

        public String icon() {
            return icon;
        }
    }

    /** One order at a time ("Po jednym zamówieniu naraz"): index of the shown order among those selected at the start. */
    public record Step(int index, int total, boolean last) {
    }

    /** Form actions; null when the button is not offered in this mode. */
    public record Actions(String commit, String commitAndContinue, String skip) {

        public boolean canContinue() {
            return commitAndContinue != null;
        }

        public boolean canSkip() {
            return skip != null;
        }
    }

    /** An order of this step: its number as the order screens show it, its link for the role, its items waiting. */
    public record OrderRef(String orderId, String number, String href, int items) {
    }

    /** Offers of one item category; name is null for items without one (shown as "Inne"). */
    public record Category(String id, String name, List<Offer> offers) {
    }

    /** An offer row: the posted entry, the connection label and its palette colour (0 = the store's warehouse). */
    public record Offer(FulfilmentGroup entry, String label, int color, boolean warehouse) {
    }

    /** A supplier present on the page, for the filter menu and the summary. */
    public record SupplierRow(String provider, String label, int color, boolean warehouse, int offers) {
    }

    /** Net value already committed at a supplier in earlier steps of the one-order-at-a-time mode. */
    public record SupplierTotal(String provider, String label, int color, boolean warehouse, double amount) {
    }

    /** A proposal (FulfilmentVariant): groupIds space-separated for the script, providers as labels. */
    public record VariantOption(String groupIds, long supplierCount, double total, boolean cheapest, boolean fewest,
                                String providers, boolean applied) {
    }

    /** An item no offer covers (spec D7). */
    public record Missing(String orderId, String number, String href, String name, int qty, double price, String reasonKey) {
    }

    public enum EmptyState {
        NONE(null, null, null),
        NOTHING_LEFT("fulfilment.select.empty.done.title", "fulfilment.select.empty.done.text", "fulfilment.select.empty.done.action"),
        RESTOCK_EMPTY("fulfilment.select.empty.restock.title", "fulfilment.select.empty.restock.text", "fulfilment.select.empty.restock.action");

        private final String titleKey;
        private final String textKey;
        private final String actionKey;

        EmptyState(String titleKey, String textKey, String actionKey) {
            this.titleKey = titleKey;
            this.textKey = textKey;
            this.actionKey = actionKey;
        }

        public String titleKey() {
            return titleKey;
        }

        public String textKey() {
            return textKey;
        }

        public String actionKey() {
            return actionKey;
        }
    }
}
