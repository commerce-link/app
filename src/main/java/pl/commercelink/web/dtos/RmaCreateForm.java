package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.starter.util.ConversionUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the new RMA page posts. Only these fields bind (RmaCreateController#binder): status, ids and the store never come from the request. */
@Getter
@Setter
@NoArgsConstructor
public class RmaCreateForm {

    public static final int MAX_REASON = 1000;

    private String orderId;
    private String email;
    private String from;
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Item {
        private String itemId;
        private boolean selected;
        private String qty;
        private RMAResolutionType resolution;
        private String reason;
    }

    /** A field (null for the whole page), its message key, the key's arguments and the item it is about (or null). */
    public record Error(String field, String key, Object[] args, String itemName) {
    }

    public static String field(int index, String name) {
        return "items" + index + "." + name;
    }

    public List<Item> selectedItems() {
        return items.stream().filter(Item::isSelected).toList();
    }

    public int qtyOf(Item item) {
        try {
            return Integer.parseInt(StringUtils.trimToEmpty(item.getQty()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Errors in page order, empty when the RMA can be created. reportable: the order's items that can be reported by
     * id; claims: item id to the id of an open RMA that already holds it.
     */
    public List<Error> validate(Map<String, OrderItem> reportable, Map<String, String> claims) {
        List<Error> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (selectedItems().isEmpty()) {
            errors.add(new Error(null, "rma.new.error.noItems", new Object[0], null));
            return errors;
        }
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (!item.isSelected()) {
                continue;
            }
            OrderItem orderItem = reportable.get(item.getItemId());
            // a repeated id would save two RMA items for one order item and exceed its quantity
            if (orderItem == null || !seen.add(item.getItemId())) {
                return List.of(new Error(null, "rma.new.error.itemsChanged", new Object[0], null));
            }
            String name = orderItem.getName();
            int qty = qtyOf(item);
            if (qty < 1 || qty > orderItem.getQty()) {
                errors.add(new Error(field(i, "qty"), "rma.new.error.qty", new Object[]{orderItem.getQty()}, name));
            }
            if (item.getResolution() == null) {
                errors.add(new Error(field(i, "resolution"), "rma.new.error.resolution", new Object[0], name));
            }
            if (item.getReason() != null && item.getReason().trim().length() > MAX_REASON) {
                errors.add(new Error(field(i, "reason"), "rma.new.error.reason", new Object[]{MAX_REASON}, name));
            }
            String claimedBy = claims.get(item.getItemId());
            if (claimedBy != null) {
                errors.add(new Error(field(i, "selected"), "rma.new.error.claimed",
                        new Object[]{ConversionUtil.getShortenedId(claimedBy)}, name));
            }
        }
        if (StringUtils.isBlank(email)) {
            errors.add(new Error("email", "rma.new.error.email.required", new Object[0], null));
        } else if (!FormRules.isEmail(email)) {
            errors.add(new Error("email", "rma.new.error.email.invalid", new Object[0], null));
        }
        return errors;
    }
}
