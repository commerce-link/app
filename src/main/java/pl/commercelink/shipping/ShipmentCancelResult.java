package pl.commercelink.shipping;

/** The outcome of {@link ShipmentCancelService#cancelShipping}, with the provider's reason when it FAILED. */
public record ShipmentCancelResult(ShipmentCancelOutcome outcome, String error) {

    public static ShipmentCancelResult requested() {
        return new ShipmentCancelResult(ShipmentCancelOutcome.REQUESTED, null);
    }

    public static ShipmentCancelResult rechecking() {
        return new ShipmentCancelResult(ShipmentCancelOutcome.RECHECKING, null);
    }

    public static ShipmentCancelResult cancelled() {
        return new ShipmentCancelResult(ShipmentCancelOutcome.CANCELLED, null);
    }

    public static ShipmentCancelResult failed(String error) {
        return new ShipmentCancelResult(ShipmentCancelOutcome.FAILED, error);
    }

    public static ShipmentCancelResult gone() {
        return new ShipmentCancelResult(ShipmentCancelOutcome.GONE, null);
    }
}
