package pl.commercelink.web.rma;

import pl.commercelink.web.dtos.RmaItemForm;
import pl.commercelink.web.orders.OrderLinks;

import java.util.List;
import java.util.Map;

/** Everything the RMA item page shows (spec §3–§5): header, the three-field form or its read-only copy, and the fact cards. */
public record RmaItemPage(String rmaId, String rmaItemId, String backHref, String backLabel,
                          String name, String statusLabel, String statusTone, String qty, String ean, String mfn, String shortId,
                          boolean readOnly, String closedTitle, RmaItemForm form, Map<String, String> errors, String errorMessage,
                          SerialHint serialHint, Resolution resolution, List<Fact> values, String total, Context context,
                          String savedMessage) {

    // the address the previous release also maps (RmaItemController); switch to the page address when that alias goes
    public String action() {
        return "/dashboard/rma/" + rmaId + "/items/" + rmaItemId + "/save";
    }

    public record SerialHint(String text, boolean warn) {
    }

    /** {@code actual} is null while nobody has decided how the item is resolved. */
    public record Resolution(String desired, String actual) {
    }

    public record Fact(String label, String value) {
    }

    public record Context(String rmaHref, String rmaShortId, String rmaStatusLabel, String rmaStatusTone, String source,
                          String client, String orderHref, String orderShortId, String deliveryHref, String deliveryText,
                          String deliverySupplier, List<OrderLinks.SerialHistory> serials) {
    }
}
