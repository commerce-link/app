package pl.commercelink.shipping;

public record ShipmentCreationStart(Outcome outcome, String error) {

    public enum Outcome { STARTED, REFUSED, GONE }

    static ShipmentCreationStart started() { return new ShipmentCreationStart(Outcome.STARTED, null); }

    static ShipmentCreationStart refused(String error) { return new ShipmentCreationStart(Outcome.REFUSED, error); }

    static ShipmentCreationStart gone() { return new ShipmentCreationStart(Outcome.GONE, null); }
}
