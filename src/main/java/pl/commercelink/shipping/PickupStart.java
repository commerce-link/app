package pl.commercelink.shipping;

/**
 * What ordering a pickup did: the command is on its way, the provider refused it, or nothing waits any more. A refusal
 * names its command, so one failed attempt can be told apart from the next.
 */
public record PickupStart(Outcome outcome, String commandId, String error) {

    public enum Outcome { STARTED, REFUSED, GONE }

    static PickupStart started() {
        return new PickupStart(Outcome.STARTED, null, null);
    }

    static PickupStart refused(String commandId, String error) {
        return new PickupStart(Outcome.REFUSED, commandId, error);
    }

    static PickupStart gone() {
        return new PickupStart(Outcome.GONE, null, null);
    }
}
