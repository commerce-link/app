package pl.commercelink.shipping.tracking;

import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;

/**
 * How far a tracked parcel got, stored on its ShipmentTrackings row so that every status (from a webhook or from
 * polling) takes effect once. A parcel only moves forward: nothing follows DELIVERED or EXPIRED.
 */
@Slf4j
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

    /**
     * Null for no state and for a state this version does not know (written by a newer one): a row the sweep reads
     * must not fail the whole store's sweep. Such a row is left alone ({@code ShipmentTracking.hasUnknownState}).
     */
    public static ShipmentTrackingState parse(String value) {
        if (value == null) {
            return null;
        }
        ShipmentTrackingState known = Arrays.stream(values()).filter(s -> s.name().equals(value)).findFirst().orElse(null);
        if (known == null) {
            log.warn("Unknown shipment tracking state {}, the parcel is left as it is", value);
        }
        return known;
    }

    public boolean isFinal() {
        return rank == 2;
    }
}
