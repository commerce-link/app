package pl.commercelink.web.dtos;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import pl.commercelink.orders.SerialNumbers;
import pl.commercelink.orders.rma.RMAItem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The RMA item page's form: only the three fields an operator may change. Everything else on the item is changed by
 * the RMA actions, so it is never bound from a post.
 */
@Getter
@Setter
public class RmaItemForm {

    public static final int MAX_SERIAL = 500;
    public static final int MAX_REASON = RmaCreateForm.MAX_REASON;
    public static final int MAX_COMMENT = 2000;
    // DynamoDB versions start at 1: "0" is the page of an item stored without a version, still checked on save
    public static final String NO_VERSION = "0";

    private String serialNo;
    private String reason;
    private String comment;
    // the optimistic-lock version the page was rendered with; null only for a page from before the redesign
    private String version;

    public static RmaItemForm of(RMAItem item) {
        RmaItemForm form = new RmaItemForm();
        form.serialNo = item.getSerialNo();
        form.reason = item.getReason();
        form.comment = item.getComment();
        form.version = versionOf(item);
        return form;
    }

    public static String versionOf(RMAItem item) {
        return item.getVersion() == null ? NO_VERSION : String.valueOf(item.getVersion());
    }

    /** A value saved before the limits existed (or imported from a marketplace) may stay as long as it is. */
    public Map<String, String> validate(RMAItem stored) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (tooLong(serialNo, MAX_SERIAL, stored.getSerialNo())) errors.put("serialNo", "rma.item.page.error.serialNo");
        if (tooLong(reason, MAX_REASON, stored.getReason())) errors.put("reason", "rma.item.page.error.reason");
        if (tooLong(comment, MAX_COMMENT, stored.getComment())) errors.put("comment", "rma.item.page.error.comment");
        return errors;
    }

    /** Writes the three fields onto the item; call only after validate() returned no errors. */
    public void applyTo(RMAItem item) {
        String serials = String.join(", ", SerialNumbers.parse(serialNo));
        item.setSerialNo(serials.isEmpty() ? null : serials);
        item.setReason(StringUtils.trimToNull(normalised(reason)));
        item.setComment(StringUtils.trimToNull(normalised(comment)));
    }

    private static boolean tooLong(String posted, int max, String stored) {
        String value = StringUtils.trim(normalised(posted));
        return StringUtils.length(value) > max && !value.equals(StringUtils.trim(normalised(stored)));
    }

    // a textarea posts its line breaks as CRLF
    private static String normalised(String text) {
        return text == null ? null : text.replace("\r\n", "\n");
    }
}
