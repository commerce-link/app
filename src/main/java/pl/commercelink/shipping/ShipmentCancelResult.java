package pl.commercelink.shipping;

/**
 * The outcome of {@link ShipmentCancelService#cancelShipping}, with the provider's reason when it FAILED.
 * backToRealization: an immediate CANCELLED left a Shipping order with nothing shipped, so it went back to Realization;
 * after a confirmation in the background the step back happens later and the page shows the new status on reload.
 */
public record ShipmentCancelResult(ShipmentCancelOutcome outcome, String error, boolean backToRealization) {

    public static ShipmentCancelResult requested() {
        return new ShipmentCancelResult(ShipmentCancelOutcome.REQUESTED, null, false);
    }

    public static ShipmentCancelResult rechecking() {
        return new ShipmentCancelResult(ShipmentCancelOutcome.RECHECKING, null, false);
    }

    public static ShipmentCancelResult cancelled() {
        return cancelled(false);
    }

    public static ShipmentCancelResult cancelled(boolean backToRealization) {
        return new ShipmentCancelResult(ShipmentCancelOutcome.CANCELLED, null, backToRealization);
    }

    public static ShipmentCancelResult failed(String error) {
        return new ShipmentCancelResult(ShipmentCancelOutcome.FAILED, error, false);
    }

    public static ShipmentCancelResult gone() {
        return new ShipmentCancelResult(ShipmentCancelOutcome.GONE, null, false);
    }
}
