package pl.commercelink.web.orders;

/**
 * An action of the selection row of the items table. scope names the data-* flag of a row the action works on (the
 * same predicate as OrdersManager), so the entry can show "(2 of 3)"; skippedKey explains the rest after the action and
 * is the entry's reason while none of the checked items fits. menu is the drop-down of the row the action sits in; an
 * action without one (REMOVE) stands on its own at the row's end, where shortSkippedKey is the few words it shows
 * instead of the sentence (null for the actions in a menu, which show the sentence). ALLOCATE is not in the selection
 * row at all (inSelectionRow): the item's own menu posts it for that one item, through the same endpoint and result.
 */
public enum BulkAction {

    ALLOCATE("moveSelectedItemsToAllocation", "order.bulk.allocate", "ready-for-allocation", "order.bulk.skipped.allocation", false, null) {
        @Override
        public boolean inSelectionRow() { return false; }
    },
    SPLIT("splitOrder", "order.bulk.split", "movable", "order.bulk.skipped.movable", false, Menu.MOVE),
    MOVE("moveItemsToOrder", "order.bulk.move", "movable", "order.bulk.skipped.movable", false, Menu.MOVE),
    TO_WAREHOUSE("moveSelectedItemsToTheWarehouse", "order.bulk.warehouse", "allocated-product", "order.bulk.skipped.warehouse", false, Menu.MOVE),
    TO_WAREHOUSE_RMA("moveSelectedItemsToTheWarehouseForRMA", "order.bulk.warehouse.rma", "delivered-product", "order.bulk.skipped.warehouse.rma", false, Menu.MOVE),
    REMOVE("removeSelectedItemsFromOrder", "order.bulk.remove", "removable", "order.bulk.skipped.remove", true, null) {
        @Override
        public String shortSkippedKey() { return "order.bulk.skipped.remove.short"; }
    };

    /** A drop-down of the selection row: "Move" (to a new or an existing order, to the warehouse). */
    public enum Menu {
        MOVE("order.bulk.menu.move");

        private final String labelKey;

        Menu(String labelKey) {
            this.labelKey = labelKey;
        }

        public String labelKey() { return labelKey; }
    }

    private final String path;
    private final String labelKey;
    private final String scope;
    private final String skippedKey;
    private final boolean danger;
    private final Menu menu;

    BulkAction(String path, String labelKey, String scope, String skippedKey, boolean danger, Menu menu) {
        this.path = path;
        this.labelKey = labelKey;
        this.scope = scope;
        this.skippedKey = skippedKey;
        this.danger = danger;
        this.menu = menu;
    }

    public String path() { return path; }
    public String labelKey() { return labelKey; }
    public String scope() { return scope; }
    public String skippedKey() { return skippedKey; }
    public boolean danger() { return danger; }
    public Menu menu() { return menu; }
    public String shortSkippedKey() { return null; }
    public boolean inSelectionRow() { return true; }
    public String confirmTitleKey() { return labelKey + ".confirm.title"; }
    public String confirmMessageKey() { return labelKey + ".confirm.message"; }
    public String confirmActionKey() { return labelKey + ".confirm.action"; }
}
