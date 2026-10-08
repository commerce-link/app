package pl.commercelink.web.fulfilment;

import java.util.List;

/**
 * The fulfilment queue page (spec docs/active/fulfilment-queue-redesign): the current group of orders waiting for a
 * supplier, what was skipped to get here, and where the form goes. skipOrderIds feeds "Pomiń grupę" (the skipped orders
 * plus this group, with skipGroups the size of each group), so the next GET leaves them all out;
 * backHref is the queue before the last skip and canRestart offers the oldest group once more than one group was skipped.
 */
public record FulfilmentQueuePage(GroupKind kind, String storeName, List<FulfilmentQueueRow> rows, int skippedCount,
                                  List<String> skipOrderIds, String skipGroups, String backHref, boolean canRestart,
                                  String postAction, int itemsTotal, EmptyState emptyState, boolean superAdmin) {

    public boolean hasGroup() {
        return !rows.isEmpty();
    }

    public boolean nothingWaiting() {
        return emptyState == EmptyState.NONE_WAITING;
    }

    public boolean allSkipped() {
        return emptyState == EmptyState.ALL_SKIPPED;
    }

    /** A group has one fulfilment type: FulfilmentQueue gathers every warehouse order of the store, or one dropship order. */
    public enum GroupKind {
        WAREHOUSE("fulfilment.queue.group.warehouse.title", "fulfilment.queue.group.warehouse.description",
                "order.fulfilment.short.WarehouseFulfilment", "fa-warehouse"),
        DROPSHIP("fulfilment.queue.group.dropship.title", "fulfilment.queue.group.dropship.description",
                "order.fulfilment.short.DirectToConsumer", "fa-shipping-fast");

        private final String titleKey;
        private final String descriptionKey;
        private final String pillKey;
        private final String icon;

        GroupKind(String titleKey, String descriptionKey, String pillKey, String icon) {
            this.titleKey = titleKey;
            this.descriptionKey = descriptionKey;
            this.pillKey = pillKey;
            this.icon = icon;
        }

        public String titleKey() {
            return titleKey;
        }

        public String descriptionKey() {
            return descriptionKey;
        }

        public String pillKey() {
            return pillKey;
        }

        public String icon() {
            return icon;
        }
    }

    public enum EmptyState {
        NONE_WAITING,
        ALL_SKIPPED
    }
}
