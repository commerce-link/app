package pl.commercelink.web.deliveries.details;

import org.springframework.context.MessageSource;
import org.springframework.ui.Model;
import pl.commercelink.documents.Document;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.web.orders.OrderConfirmPages;
import pl.commercelink.web.settings.ConfirmAction;

import java.util.Locale;

/**
 * Delivery actions confirmed on their own page when JavaScript is off; with JavaScript the confirmation dialog posts to
 * the same address (confirm-dialog.js), so each page's POST is the action itself. Deleting is red; unlinking an
 * invoice can be undone by linking it again, so it confirms with the primary button.
 */
public final class DeliveryConfirmPages {

    private DeliveryConfirmPages() {
    }

    public static String delete(Model model, Delivery delivery, String supplierName, DeliveryLinks links,
                                MessageSource messages, Locale locale) {
        String shortId = delivery.getShortenedDeliveryId();
        return OrderConfirmPages.render(model, new ConfirmAction(
                        messages.getMessage("deliveries.details.delete.title", new Object[]{shortId}, locale),
                        messages.getMessage("deliveries.details.delete.message", new Object[]{shortId, supplierName}, locale),
                        messages.getMessage("deliveries.details.delete.action", null, locale),
                        links.confirm("delete"), links.details(), true),
                messages.getMessage("deliveries.details.confirm.back", new Object[]{shortId}, locale));
    }

    public static String unlinkInvoice(Model model, Delivery delivery, Document invoice, DeliveryLinks links,
                                       MessageSource messages, Locale locale) {
        return OrderConfirmPages.render(model, new ConfirmAction(
                        messages.getMessage("deliveries.details.unlink.title", new Object[]{invoice.getNumber()}, locale),
                        messages.getMessage("deliveries.details.unlink.message", null, locale),
                        messages.getMessage("deliveries.details.unlink.action", null, locale),
                        links.unlinkInvoice(invoice.getId()), links.details(), false),
                messages.getMessage("deliveries.details.confirm.back", new Object[]{delivery.getShortenedDeliveryId()}, locale));
    }
}
