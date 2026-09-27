package pl.commercelink.web.orders;

/**
 * An action of the selection row of the items table. scope names the data-* flag of a row the action works on (the
 * same predicate as OrdersManager), so the entry can show "(2 of 3)"; skippedKey explains the rest after the action and
 * is the entry's reason while none of the checked items fits. menu is the drop-down of the row the action sits in; an
 * action without one (REMOVE) stands on its own at the row's end.
 */
public enum BulkAction {

    ALLOCATE("moveSelectedItemsToAllocation", "order.bulk.allocate", "ready-for-allocation", "order.bulk.skipped.allocation", false, Menu.ROUTE),
    TO_WAREHOUSE("moveSelectedItemsToTheWarehouse", "order.bulk.warehouse", "allocated-product", "order.bulk.skipped.warehouse", false, Menu.ROUTE),
    TO_WAREHOUSE_RMA("moveSelectedItemsToTheWarehouseForRMA", "order.bulk.warehouse.rma", "delivered-product", "order.bulk.skipped.warehouse.rma", false, Menu.ROUTE),
    SPLIT("splitOrder", "order.bulk.split", "movable", "order.bulk.skipped.movable", false, Menu.MOVE),
    MOVE("moveItemsToOrder", "order.bulk.move", "movable", "order.bulk.skipped.movable", false, Menu.MOVE),
    REMOVE("removeSelectedItemsFromOrder", "order.bulk.remove", "removable", "order.bulk.skipped.remove", true, null);

    /** A drop-down of the selection row: "Route to" (allocation, warehouse) and "Move" (to a new or an existing order). */
    public enum Menu {
        ROUTE("order.bulk.menu.route"),
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
    public String confirmTitleKey() { return labelKey + ".confirm.title"; }
    public String confirmMessageKey() { return labelKey + ".confirm.message"; }
    public String confirmActionKey() { return labelKey + ".confirm.action"; }
}
