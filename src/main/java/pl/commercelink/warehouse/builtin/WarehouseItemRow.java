package pl.commercelink.warehouse.builtin;

import java.util.List;

/**
 * One row of the warehouse list with every text resolved (spec §4.4); the template prints, never computes. deliveryHref
 * and deliveryNumber are null when the item carries no delivery id (the cell shows a dash, or supplier as plain text when
 * it names a connected supplier). codes are the EAN (with its label) and the manufacturer code as separate parts, so a
 * line breaks between them and never inside one. systemCost is the whole hint of the system cost (net and gross), shown
 * behind an icon. hasDelivery tells whether the item carries a delivery id, which goods out and shipping need.
 */
public record WarehouseItemRow(String itemId, String href, String name, List<String> codes, String conditionLabel,
                               String conditionTone, String comment, String categoryLabel, boolean uncategorized, int qty,
                               String costNet, String costGross, String systemCost,
                               String deliveryHref, String deliveryNumber, String supplier, String serialNo, String status,
                               String statusLabel, String statusTone, boolean selectable, boolean hasDelivery) {
}
