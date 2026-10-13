package pl.commercelink.shipping;

/**
 * What starting a shipment creation did. A refusal says whether its reason is the provider's own answer (a 4xx body or
 * a failure the provider reported), which may be shown to a customer, or the adapter's words for a command it gave up
 * on before the provider answered, which only an operator should read.
 */
public record ShipmentCreationStart(Outcome outcome, String error, boolean providerAnswer) {

    public enum Outcome { STARTED, REFUSED, GONE }

    static ShipmentCreationStart started() { return new ShipmentCreationStart(Outcome.STARTED, null, false); }

    /** A started creation, for tests outside this package. */
    public static ShipmentCreationStart startedForTest() { return started(); }

    static ShipmentCreationStart refused(String error, boolean providerAnswer) {
        return new ShipmentCreationStart(Outcome.REFUSED, error, providerAnswer);
    }

    static ShipmentCreationStart gone() { return new ShipmentCreationStart(Outcome.GONE, null, false); }
}
