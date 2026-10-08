package pl.commercelink.warehouse.builtin;

import java.util.List;

/**
 * One row of the warehouse list with every text resolved (spec §4.4); the template prints, never computes. deliveryHref
 * and deliveryNumber are null when the item's delivery is not a delivery of the store (the cell shows a dash); supplier
 * is the label of the delivery's supplier, null when the link text already names the supplier. codes are the EAN (with its
 * label) and the manufacturer code as separate parts, so a line breaks between them and never inside one. A row with no
 * link but a supplier names that supplier as plain text. hasDelivery tells whether the item came from a delivery record
 * of the store, which goods out and shipping need.
 */
public record WarehouseItemRow(String itemId, String href, String name, List<String> codes, String conditionLabel,
                               String conditionTone, String comment, String categoryLabel, boolean uncategorized, int qty,
                               String costNet, String costGross, String systemCost, String systemCostTitle,
                               String deliveryHref, String deliveryNumber, String supplier, String serialNo, String status,
                               String statusLabel, String statusTone, boolean selectable, String source,
                               boolean hasDelivery) {
}
