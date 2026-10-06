package pl.commercelink.shipping;

/** What ordering a pickup did: the command is on its way, the provider refused it, or nothing waits any more. */
public record PickupStart(Outcome outcome, String error) {

    public enum Outcome { STARTED, REFUSED, GONE }

    static PickupStart started() {
        return new PickupStart(Outcome.STARTED, null);
    }

    static PickupStart refused(String error) {
        return new PickupStart(Outcome.REFUSED, error);
    }

    static PickupStart gone() {
        return new PickupStart(Outcome.GONE, null);
    }
}
