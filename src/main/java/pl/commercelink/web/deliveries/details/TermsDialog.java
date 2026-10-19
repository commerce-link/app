package pl.commercelink.web.deliveries.details;

import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.web.dtos.DeliveryTermsForm;

import java.util.Map;

/**
 * The "Terminy i koszty" and "Komentarz" dialogs: the address they post to (the store's own or the super admin's), the
 * values shown (the delivery's, or what the operator typed when the save came back with errors), the errors per field,
 * a refusal of the whole save, and the message async-form.js toasts after a save.
 */
public record TermsDialog(String action, String deliveryId, String shortId, boolean dateRequired, DeliveryTermsForm form,
                          Map<String, String> errors, String refusal, String savedMessage) {

    public static TermsDialog of(Delivery delivery, DeliveryLinks links) {
        // a dropship delivery may have no planned date, as on the create page (create spec D7)
        return new TermsDialog(links.saveTerms(), delivery.getDeliveryId(), delivery.getShortenedDeliveryId(),
                !delivery.isDropship(), DeliveryTermsForm.of(delivery), Map.of(), null, null);
    }

    public TermsDialog withErrors(DeliveryTermsForm submitted, Map<String, String> errors) {
        return new TermsDialog(action, deliveryId, shortId, dateRequired, submitted, errors, null, null);
    }

    public TermsDialog refused(DeliveryTermsForm submitted, String refusal) {
        return new TermsDialog(action, deliveryId, shortId, dateRequired, submitted, Map.of(), refusal, null);
    }

    public TermsDialog saved(DeliveryTermsForm stored, String savedMessage) {
        return new TermsDialog(action, deliveryId, shortId, dateRequired, stored, Map.of(), null, savedMessage);
    }
}
