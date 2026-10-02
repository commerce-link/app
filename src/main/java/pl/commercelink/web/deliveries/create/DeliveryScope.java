package pl.commercelink.web.deliveries.create;

import org.springframework.ui.Model;
import pl.commercelink.inventory.deliveries.PurchaseSubmission;
import pl.commercelink.inventory.deliveries.PurchaseValidation;
import pl.commercelink.orders.Order;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.web.dtos.DeliveryCreationForm;
import pl.commercelink.web.dtos.SuggestedDeliveryItem;

import java.util.List;

/**
 * What a new delivery covers and how it is ordered: the warehouse batch of one supplier (every waiting allocation,
 * goods to the store's warehouse) or one order's lines at one supplier (dropship, goods to the customer). The create
 * pages are the same for both; the scope supplies the items and delegates ordering to the existing purchase services,
 * whose logic stays separate (the path spends money).
 */
public interface DeliveryScope {

    String storeId();

    String provider();

    /** The order of a dropship delivery; null for a warehouse delivery. */
    Order order();

    default boolean dropship() {
        return order() != null;
    }

    /**
     * Step 1 form built from what waits at the supplier now; null when nothing is left to order there. It carries no
     * restock suggestions: those take seconds to work out, so the page fetches them on its own ({@link #suggestions()}).
     */
    DeliveryCreationForm plannedForm();

    /**
     * Products worth ordering for the warehouse while ordering anyway (restock suggestions; warehouse only). Slow: it
     * reads the price aggregates and the suppliers' offers, so step 1 asks for it after the page is shown.
     */
    default List<SuggestedDeliveryItem> suggestions() {
        return List.of();
    }

    /** VAT multiplier a fresh form starts with (1 for a known foreign supplier, the VAT rate otherwise). */
    double defaultTax();

    /** Whether "Zamów przez integrację" is offered (warehouse: an ordering adapter; dropship: eligibility guarantees it). */
    boolean purchaseAvailable();

    /** Message key of why the integration refuses this delivery (an unsupported pickup point), or null. */
    String purchaseBlockedReason();

    /** A GLOBAL connection: the order waits for the platform administrator's approval. */
    boolean requiresApproval();

    /** Whether a delivery recorded outside the system needs the supplier order number and date (warehouse). */
    boolean requiresOrderIdentity();

    /** Address book and supplier options of step 2 (integration). */
    void addPurchaseModel(DeliveryCreationForm form, Model model);

    /** Live availability; throws when the supplier cannot be asked (the page then offers a retry). */
    PurchaseValidation validate(DeliveryCreationForm form);

    OperationResult<PurchaseSubmission> submit(DeliveryCreationForm form, String purchaseRef);

    /** Records a delivery ordered outside the system; the payload is the delivery id. */
    OperationResult<String> save(DeliveryCreationForm form);

    /** Dropship only: hands the unticked lines back without creating a delivery. */
    default void releaseUnselected(DeliveryCreationForm form) {
        throw new UnsupportedOperationException("Releasing without a delivery is a dropship action");
    }
}
