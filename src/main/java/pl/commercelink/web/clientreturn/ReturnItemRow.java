package pl.commercelink.web.clientreturn;

import pl.commercelink.orders.rma.RMAResolutionType;

/** One returned product as the customer reads it: statusKey and actual are null where the page shows neither. */
public record ReturnItemRow(String name, int qty, RMAResolutionType desired, RMAResolutionType actual, String reason,
                            String statusKey, String statusTone) {
}
