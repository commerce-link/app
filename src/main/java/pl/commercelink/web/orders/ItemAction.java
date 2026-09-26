package pl.commercelink.web.orders;

/** The row menu of an order item. dialogId names the dialog the entry opens; the others post or link. */
public enum ItemAction {

    ASSIGN_SKU("order.item.menu.assign.sku", "assign-sku-dialog"),
    ASSIGN_SUPPLIER("order.item.menu.assign.supplier", "assign-supplier-dialog"),
    ASSIGN_WAREHOUSE("order.item.menu.assign.warehouse", "assign-warehouse-dialog"),
    CLEAR_SUPPLIER("order.item.menu.clear.supplier", null),
    SPLIT_GROUP("order.item.menu.split.group", "split-group-dialog"),
    CONSOLIDATE("order.item.menu.consolidate", null),
    EDIT("order.item.menu.edit", null);

    private final String labelKey;
    private final String dialogId;

    ItemAction(String labelKey, String dialogId) {
        this.labelKey = labelKey;
        this.dialogId = dialogId;
    }

    public String labelKey() {
        return labelKey;
    }

    public String dialogId() {
        return dialogId;
    }

    /** One entry as the menu shows it: available, or greyed out with the reason in a second line (spec B11). */
    public record State(ItemAction action, String labelKey, boolean available, String reasonKey, String reasonArg) {

        public static State of(ItemAction action, String reasonKey, String reasonArg) {
            return new State(action, action.labelKey(), reasonKey == null, reasonKey, reasonArg);
        }
    }
}
