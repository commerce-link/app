package pl.commercelink.web.orders;

/**
 * The selection bar of the items table. scope names the data-* flag of a row the action works on (the same predicate as
 * OrdersManager), so the button can show "(2 of 3)"; skippedKey explains the rest after the action.
 */
public enum BulkAction {

    ALLOCATE("moveSelectedItemsToAllocation", "order.bulk.allocate", "ready-for-allocation", "order.bulk.skipped.allocation", false),
    TO_WAREHOUSE("moveSelectedItemsToTheWarehouse", "order.bulk.warehouse", "allocated-product", "order.bulk.skipped.warehouse", false),
    TO_WAREHOUSE_RMA("moveSelectedItemsToTheWarehouseForRMA", "order.bulk.warehouse.rma", "delivered-product", "order.bulk.skipped.warehouse.rma", false),
    SPLIT("splitOrder", "order.bulk.split", "movable", "order.bulk.skipped.movable", false),
    MOVE("moveItemsToOrder", "order.bulk.move", "movable", "order.bulk.skipped.movable", false),
    REMOVE("removeSelectedItemsFromOrder", "order.bulk.remove", "removable", "order.bulk.skipped.remove", true);

    private final String path;
    private final String labelKey;
    private final String scope;
    private final String skippedKey;
    private final boolean danger;

    BulkAction(String path, String labelKey, String scope, String skippedKey, boolean danger) {
        this.path = path;
        this.labelKey = labelKey;
        this.scope = scope;
        this.skippedKey = skippedKey;
        this.danger = danger;
    }

    public String path() { return path; }
    public String labelKey() { return labelKey; }
    public String scope() { return scope; }
    public String skippedKey() { return skippedKey; }
    public boolean danger() { return danger; }
    public String confirmTitleKey() { return labelKey + ".confirm.title"; }
    public String confirmMessageKey() { return labelKey + ".confirm.message"; }
    public String confirmActionKey() { return labelKey + ".confirm.action"; }
}
