package pl.commercelink.orders.rma;

import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;

import java.util.List;

/**
 * The shipments card of an RMA (rma/detail/shipments.html) with the state of shipments created through an integration.
 * rows: every shipment of RMA.getShipments(), in that order. pickupHref: "Zamów odbiór", the pickup page preset to the
 * first package waiting for a courier, null when none waits or the RMA is closed. pollHref: the JSON shipments state the
 * page polls while a command of one of its shipments waits for the provider, null when none waits. editable: shipments
 * can be added and edited (an open RMA). updateAction: the POST of the shipment dialogs, which carries every shipment.
 * types: the choices of the type select.
 */
public record RmaShipmentsView(List<Row> rows, String pickupHref, String pollHref, boolean editable,
                               String updateAction, List<ShipmentType> types) {

    public String emptyKey() {
        return editable ? "rma.detail.shipments.empty" : "order.shipments.empty.readonly";
    }

    /**
     * number: 1-based position in RMA.getShipments(), the n of ?open=shipment-{n}. typeKey: the message key of the type,
     * null without one. shippedAt/deliveredAt: "dd.MM.yyyy" or null. trackingHref: the tracking link, only an http(s)
     * address (a stored javascript: URL is shown as text, never as a link), null otherwise. editable: "Edytuj" (an open RMA, a shipment not
     * being created). stateKey with stateArgs and stateTone: the state line (OrderLabels#shipmentState), null for a
     * shipment typed in by hand or a customer's return that needs nothing (RmaShipmentsViewFactory); stateInProgress: it
     * waits for the provider (a spinner). labelHref: "Pobierz etykietę", never for a customer's return, whose label the
     * courier brings. retryHref: "Spróbuj ponownie" after a failed operator shipment, the items card where the items are
     * shipped again. removeAction: the POST that drops a failed creation. pickupRetryAction: the POST of "Zamów odbiór
     * ponownie", for a customer's return whose pickup was not ordered. returnRetryAction: the POST of "Spróbuj ponownie"
     * for a customer's return that failed to be created, booked again with what the customer chose
     * (CustomerReturnRetry); null when that cannot be done, "Usuń" stays.
     */
    public record Row(Shipment shipment, int number, String typeKey, String shippedAt, String deliveredAt,
                      String trackingHref, boolean editable, String stateKey, Object[] stateArgs, String stateTone, boolean stateInProgress,
                      String labelHref, String retryHref, String removeAction, String pickupRetryAction,
                      String returnRetryAction) {

        /** The position in RMA.getShipments(), the i of the posted shipments[i].*. */
        public int index() {
            return number - 1;
        }

        public String dialogId() {
            return "shipment-dialog-" + number;
        }

        public String openKey() {
            return "shipment-" + number;
        }

        public boolean hasMenu() {
            return editable || removeAction != null || pickupRetryAction != null;
        }

        public boolean hasActions() {
            return labelHref != null || retryHref != null || returnRetryAction != null || hasMenu();
        }
    }
}
