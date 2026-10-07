package pl.commercelink.orders.rma;

import pl.commercelink.orders.Shipment;

import java.util.List;

/**
 * The shipments table of an RMA (rma-detail.html) with the state of shipments created through an integration.
 * pickupHref: "Zamów odbiór", the pickup page preset to the first package waiting for a courier, null when none waits.
 * pollHref: the JSON shipments state the page polls while a command of one of its shipments waits for the provider,
 * null when none waits.
 */
public record RmaShipmentsView(List<Row> rows, String pickupHref, String pollHref) {

    /**
     * stateKey with stateArgs and stateTone: the state line (OrderLabels#shipmentState), null for a shipment typed in
     * by hand or a customer's return that needs nothing (RmaShipmentsViewFactory). labelHref: "Pobierz etykietę",
     * never for a customer's return, whose label the courier brings. retryHref: "Spróbuj ponownie" after a failed operator shipment, the item
     * list of the page where the items are shipped again. removeAction: the POST that drops a failed creation.
     * pickupRetryAction: the POST of "Zamów odbiór ponownie", for a customer's return whose pickup was not ordered.
     * returnRetryAction: the POST of "Spróbuj ponownie" for a customer's return that failed to be created, booked again
     * with what the customer chose (CustomerReturnRetry); null when that cannot be done, "Usuń" stays.
     */
    public record Row(Shipment shipment, String stateKey, Object[] stateArgs, String stateTone, String labelHref,
                      String retryHref, String removeAction, String pickupRetryAction, String returnRetryAction) {

        public boolean hasActions() {
            return labelHref != null || retryHref != null || removeAction != null || pickupRetryAction != null
                    || returnRetryAction != null;
        }
    }
}
