package pl.commercelink.inventory.deliveries;

import java.util.Arrays;
import java.util.Optional;

/**
 * What the deliveries list says about a delivery (spec §4): the first state that applies, in declaration order, which
 * is also the order of the Status sort and of the "Stan" menu. A received dropship delivery was shipped to the
 * customer, not delivered: receivedAt is set when the supplier confirms the shipment.
 */
public enum DeliveryListState {
    AWAITING_APPROVAL("awaitingApproval", "is-warn", false, false),
    ORDER_PENDING("orderPending", "is-info", false, false),
    OUTCOME_UNKNOWN("outcomeUnknown", "is-warn", false, true),
    DISPATCHED("dispatched", "is-warn", false, false),
    FAILED("failed", "is-bad", false, true),
    RECEIVED("received", "is-ok", true, false),
    SHIPPED_TO_CUSTOMER("shippedToCustomer", "is-ok", true, false),
    CANCELLED_BY_SUPPLIER("cancelledBySupplier", "is-bad", false, true),
    SHIPPED_WITHOUT_DATA("shippedWithoutData", "is-warn", false, true),
    AWAITING_SHIPMENT("awaitingShipment", "is-info", false, false),
    IN_TRANSIT("inTransit", "is-info", false, false);

    private final String param;
    private final String tone;
    private final boolean received;
    private final boolean problem;

    DeliveryListState(String param, String tone, boolean received, boolean problem) {
        this.param = param;
        this.tone = tone;
        this.received = received;
        this.problem = problem;
    }

    public static DeliveryListState of(Delivery delivery) {
        if (delivery.isAwaitingApproval()) {
            return AWAITING_APPROVAL;
        }
        if (delivery.isOrderPending()) {
            return ORDER_PENDING;
        }
        if (delivery.isOrderOutcomeUnknown()) {
            return OUTCOME_UNKNOWN;
        }
        if (delivery.isOrderDispatched()) {
            return DISPATCHED;
        }
        if (delivery.isOrderFailed()) {
            return FAILED;
        }
        if (delivery.hasBeenReceived()) {
            return delivery.isDropship() ? SHIPPED_TO_CUSTOMER : RECEIVED;
        }
        if (!delivery.isDropship()) {
            return IN_TRANSIT;
        }
        DeliveryTrackingState tracking = delivery.getTrackingView().getState();
        if (tracking == DeliveryTrackingState.CANCELLED_BY_SUPPLIER) {
            return CANCELLED_BY_SUPPLIER;
        }
        if (tracking == DeliveryTrackingState.SHIPPED_WITHOUT_DATA) {
            return SHIPPED_WITHOUT_DATA;
        }
        return AWAITING_SHIPMENT;
    }

    public static Optional<DeliveryListState> parse(String value) {
        return value == null ? Optional.empty()
                : Arrays.stream(values()).filter(s -> s.param.equalsIgnoreCase(value.trim())).findFirst();
    }

    public String param() {
        return param;
    }

    public String messageKey() {
        return "deliveries.list.state." + param;
    }

    public String tone() {
        return tone;
    }

    public boolean isReceived() {
        return received;
    }

    /**
     * Still expected at the warehouse or the customer, so a passed planned date means late. A failed order or a delivery
     * cancelled by the supplier is no longer coming: "overdue" next to it would contradict the state itself.
     */
    public boolean expectsArrival() {
        return !received && this != FAILED && this != CANCELLED_BY_SUPPLIER;
    }

    /** Needs an operator's decision: the "Do wyjaśnienia" tile. DISPATCHED is transient and turns into OUTCOME_UNKNOWN. */
    public boolean isProblem() {
        return problem;
    }
}
