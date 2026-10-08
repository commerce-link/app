package pl.commercelink.shipping.tracking;

/**
 * How far a tracked parcel got, stored on its ShipmentTrackings row so that every status (from a webhook or from
 * polling) takes effect once. A parcel only moves forward: nothing follows DELIVERED or EXPIRED.
 */
public enum ShipmentTrackingState {
    COLLECTED(1),
    DELIVERED(2),
    EXPIRED(2);

    private final int rank;

    ShipmentTrackingState(int rank) {
        this.rank = rank;
    }

    /** {@code from == null}: the parcel has not been collected yet. A repeated state is not a move. */
    public static boolean isForward(ShipmentTrackingState from, ShipmentTrackingState to) {
        int fromRank = from == null ? 0 : from.rank;
        return to.rank > fromRank;
    }

    public static ShipmentTrackingState parse(String value) {
        return value == null ? null : valueOf(value);
    }

    public boolean isFinal() {
        return rank == 2;
    }
}
