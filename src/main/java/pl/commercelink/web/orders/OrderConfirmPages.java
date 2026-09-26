package pl.commercelink.web.orders;

import org.springframework.ui.Model;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.web.settings.ConfirmAction;

/** An order action confirmed on its own page when JavaScript is off (the page the confirmation dialog's link points to). */
public final class OrderConfirmPages {

    public static final String VIEW = "settings-confirm";
    public static final String INVOICING_VIEW = "orders/invoicing-confirm";

    private OrderConfirmPages() {
    }

    public static String render(Model model, ConfirmAction action, String backLabel) {
        model.addAttribute("confirm", action);
        model.addAttribute("backLabel", backLabel);
        return VIEW;
    }

    /** Issuing an invoice: besides the question it carries the "send to the customer" checkbox of the issue dialog. */
    public static String renderInvoicing(Model model, String orderId, DocumentType documentType, String backLabel) {
        model.addAttribute("orderId", orderId);
        model.addAttribute("documentType", documentType.name());
        model.addAttribute("documentLabelKey", OrderLabels.documentType(documentType));
        model.addAttribute("backLabel", backLabel);
        return INVOICING_VIEW;
    }
}
