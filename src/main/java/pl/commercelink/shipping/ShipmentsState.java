package pl.commercelink.shipping;

/**
 * What a page with shipments polls while one of them waits for the provider (shipment-cancellation.js): inProgress
 * stays true until no cancellation, creation or pickup order of the owner waits any more.
 */
public record ShipmentsState(boolean inProgress) {
}
