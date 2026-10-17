package pl.commercelink.web.rma;

/**
 * The return address page of an RMA. fillAvailable: the order has a shipping address that differs from the form;
 * filled: the form shows the order's address, not saved yet; missing: the RMA has no address at all;
 * incompleteForShipping: "Ship to client" has just sent the operator here because the address is incomplete;
 * shipToClient: the operator came from "Ship to client", kept through a failed save so the saved message can say what
 * to do next.
 */
public record RmaAddressPage(String rmaId, String rmaShortId, String orderShortId, RmaAddressForm form,
                             boolean fillAvailable, boolean filled, boolean missing, boolean incompleteForShipping,
                             boolean shipToClient) {

    /** Posted by the form when the operator came from "Ship to client" (the template writes the same name). */
    public static final String SHIP_TO_CLIENT_PARAM = "shipToClient";

    public static String shortId(String id) {
        return id == null ? null : id.length() > 8 ? id.substring(0, 8) : id;
    }

    /** Arriving from "Ship to client" nothing was submitted, so "Changes were not saved" would be false. */
    public String errorSummaryTitleKey() {
        return incompleteForShipping ? "rma.address.incomplete.for.shipping" : "form.errors.title";
    }

    /** The "no address yet" warning; left out next to an error summary, which already says what is missing. */
    public boolean showMissingWarning() {
        return missing && !filled && form.errors().isEmpty();
    }

    /**
     * Arriving from "Ship to client" without any address the warning stands in for the error summary: it takes the focus
     * and says why the operator is here, while the empty fields are not marked before anything is submitted.
     */
    public boolean focusMissingWarning() {
        return incompleteForShipping && showMissingWarning();
    }

    public String missingWarningKey() {
        if (incompleteForShipping) {
            return fillAvailable ? "rma.address.missing.for.shipping.with.order" : "rma.address.missing.for.shipping";
        }
        return fillAvailable ? "rma.address.missing.with.order" : "rma.address.missing";
    }
}
