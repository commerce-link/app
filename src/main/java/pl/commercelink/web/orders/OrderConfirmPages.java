package pl.commercelink.web.orders;

import org.springframework.ui.Model;
import pl.commercelink.web.settings.ConfirmAction;

/** An order action confirmed on its own page when JavaScript is off (the page the confirmation dialog's link points to). */
public final class OrderConfirmPages {

    public static final String VIEW = "settings-confirm";

    private OrderConfirmPages() {
    }

    public static String render(Model model, ConfirmAction action, String backLabel) {
        model.addAttribute("confirm", action);
        model.addAttribute("backLabel", backLabel);
        return VIEW;
    }
}
