package pl.commercelink.receipts;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.stores.PosReceiptMode;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The receipt decision the order status form asks for when a point-of-sale order moves to Delivered: a receipt
 * printed on the shop's cash register (recorded with its number) or an e-receipt to the customer's own e-mail. It is
 * applied to the order before the save that makes it Delivered, so the automatic trigger already sees it. Other
 * paths to Delivered (shipments form, carrier webhooks) never see this form; {@link ReceiptEligibility} keeps them
 * safe by raising an alert instead of issuing.
 */
@Component
public class PosReceiptDecisions {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final ReceiptEligibility eligibility;
    private final ReceiptAttemptService attemptService;

    public PosReceiptDecisions(ReceiptEligibility eligibility, @Lazy ReceiptAttemptService attemptService) {
        this.eligibility = eligibility;
        this.attemptService = attemptService;
    }

    /** The store's POS mode when moving this order to {@code newStatus} needs the operator's decision. */
    public Optional<PosReceiptMode> required(Store store, Order order, OrderStatus newStatus) {
        if (store == null || newStatus != OrderStatus.Delivered || order.getStatus() == OrderStatus.Delivered
                || !order.isPointOfSale() || !eligibility.storeReady(store) || !eligibility.orderQualifies(order)
                || eligibility.posEReceiptChosen(store, order)
                || !attemptService.attemptsOf(order.getStoreId(), order.getOrderId()).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(store.getReceiptConfiguration().getPosReceiptMode());
    }

    /**
     * Applies the decision to the order (the caller saves it); returns the message key of a refusal, after which the
     * caller must not save the order.
     */
    public Optional<String> apply(Store store, PosReceiptMode mode, Order order, PosReceiptDecisionForm form,
                                  LocalDate today) {
        PosReceiptMode choice = form == null ? null : form.getChoice();
        boolean allowed = choice == PosReceiptMode.CASH_REGISTER && mode != PosReceiptMode.E_RECEIPT
                || choice == PosReceiptMode.E_RECEIPT && mode != PosReceiptMode.CASH_REGISTER;
        if (!allowed) {
            return Optional.of("receipts.pos.decision.choiceRequired");
        }
        if (choice == PosReceiptMode.CASH_REGISTER) {
            String number = StringUtils.strip(form.getReceiptNumber());
            if (StringUtils.isEmpty(number)) {
                return Optional.of("receipts.pos.decision.numberRequired");
            }
            order.addDocument(new Document(null, number, null, DocumentType.Receipt, today));
            return Optional.empty();
        }
        String email = StringUtils.strip(form.getCustomerEmail());
        if (email == null || !EMAIL.matcher(email).matches()) {
            return Optional.of("receipts.pos.decision.emailRequired");
        }
        order.getBillingDetails().setEmail(email);
        if (BuyerEmail.of(order, store) == null) {
            return Optional.of("receipts.pos.decision.emailIsStores");
        }
        order.setPosEReceiptRequested(true);
        return Optional.empty();
    }
}
