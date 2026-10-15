package pl.commercelink.web.rma;

/**
 * The return address page of an RMA. fillAvailable: the order has a shipping address that differs from the form;
 * filled: the form shows the order's address, not saved yet; missing: the RMA has no address at all;
 * incompleteForShipping: "Ship to client" sent the operator here because the address is incomplete.
 */
public record RmaAddressPage(String rmaId, String rmaShortId, String orderShortId, RmaAddressForm form,
                             boolean fillAvailable, boolean filled, boolean missing, boolean incompleteForShipping) {

    public static String shortId(String id) {
        return id == null ? null : id.length() > 8 ? id.substring(0, 8) : id;
    }
}
