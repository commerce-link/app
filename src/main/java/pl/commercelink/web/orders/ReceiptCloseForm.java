package pl.commercelink.web.orders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * "Zamknij ręcznie" of one e-receipt attempt: the dialog on the order page and the same form on its own page without
 * JavaScript. Ids carry the attempt's number, so every dialog on the page has its own.
 */
public record ReceiptCloseForm(String orderId, String receiptKey, int attemptNo) {

    public String dialogId() {
        return "receipt-close-" + attemptNo;
    }

    public String numberId() {
        return dialogId() + "-number";
    }

    public String linkId() {
        return dialogId() + "-link";
    }

    public String helpId() {
        return dialogId() + "-help";
    }

    /** The page of the form, which the dialog's trigger links to (and which a browser without JavaScript opens). */
    public String pageHref() {
        return "/dashboard/orders/" + orderId + "/receipts/close?receiptKey="
                + URLEncoder.encode(receiptKey, StandardCharsets.UTF_8);
    }
}
