package pl.commercelink.warehouse.builtin;

/** One row of the warehouse list with every text resolved (spec §4.4); the template prints, never computes. */
public record WarehouseItemRow(String itemId, String href, String name, String codes, String conditionLabel,
                               String conditionTone, String comment, String categoryLabel, boolean uncategorized, int qty,
                               String costNet, String costGross, String systemCost, String systemCostTitle,
                               String deliveryHref, String deliveryNumber, String serialNo, String status,
                               String statusLabel, String statusTone, boolean selectable, String source) {
}
