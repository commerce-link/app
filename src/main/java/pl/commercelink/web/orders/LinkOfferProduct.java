package pl.commercelink.web.orders;

/**
 * One product the "Link to product" dialog lists, in the field names its script reads. {@code categoryId} and
 * {@code productId} name the operator's choice, since an EAN may repeat across products. {@code price} is null for a
 * product the newest pricelist does not carry.
 */
public record LinkOfferProduct(String categoryId, String productId, String ean, String name, String label, String mfn,
                               String category, Long price) {
}
