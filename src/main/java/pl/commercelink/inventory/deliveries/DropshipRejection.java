package pl.commercelink.inventory.deliveries;

/**
 * Why an order cannot be dropshipped. Carried out of the eligibility check so that the operator can be told
 * what to fix instead of being redirected in silence.
 */
public enum DropshipRejection {
    WAREHOUSE_FULFILMENT,
    NO_SHIPPING_DETAILS,
    NOTHING_ALLOCATED,
    UNSETTLED_ITEMS,
    NO_DROPSHIP_CAPABLE_SUPPLIER
}
