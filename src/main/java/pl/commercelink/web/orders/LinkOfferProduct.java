package pl.commercelink.web.orders;

/**
 * One product the "Link to product" dialog lists, in the field names its script reads (as the pricelist rows it used
 * to read). {@code price} is null for a product the newest pricelist does not carry.
 */
public record LinkOfferProduct(String pimId, String name, String label, String mfn, String category, Long price) {
}
