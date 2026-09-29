package pl.commercelink.receipts;

import org.junit.jupiter.api.Test;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.PosReceiptMode;
import pl.commercelink.stores.Store;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.commercelink.receipts.ReceiptFixtures.*;

class PosReceiptDecisionsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 29);

    private final ReceiptProviderFactory factory = mock(ReceiptProviderFactory.class);
    private final ReceiptAttemptService attemptService = mock(ReceiptAttemptService.class);
    private final PosReceiptDecisions decisions = new PosReceiptDecisions(new ReceiptEligibility(factory), attemptService);

    private Store storeWithReceipts(PosReceiptMode mode) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setConfigurationValue(IntegrationType.RECEIPT_PROVIDER, FakeReceiptProviderDescriptor.NAME);
        store.getReceiptConfiguration().enable(DELIVERED_AT.minusDays(1));
        when(factory.getDescriptor(FakeReceiptProviderDescriptor.NAME)).thenReturn(new FakeReceiptProviderDescriptor());
        return withPosMode(store, mode);
    }

    private static Order shippingPosOrder() {
        Order order = posOrder(100);
        order.setStatus(OrderStatus.Shipping);
        return order;
    }

    private static PosReceiptDecisionForm form(PosReceiptMode choice, String number, String email) {
        PosReceiptDecisionForm form = new PosReceiptDecisionForm();
        form.setChoice(choice);
        form.setReceiptNumber(number);
        form.setCustomerEmail(email);
        return form;
    }

    @Test
    void asksWhenAPosOrderGoesToDeliveredWithoutAReceipt() {
        // when / then
        assertThat(decisions.required(storeWithReceipts(PosReceiptMode.ASK), shippingPosOrder(), OrderStatus.Delivered))
                .contains(PosReceiptMode.ASK);
    }

    @Test
    void doesNotAskWhenAReceiptIsAlreadyRecorded() {
        // given
        Order order = shippingPosOrder();
        order.addDocument(new Document(null, "7/2026", null, DocumentType.Receipt, TODAY));

        // when / then
        assertThat(decisions.required(storeWithReceipts(PosReceiptMode.CASH_REGISTER), order, OrderStatus.Delivered)).isEmpty();
    }

    @Test
    void doesNotAskInEReceiptModeWhenTheCustomersEmailIsKnown() {
        // given
        Order order = shippingPosOrder();
        order.getBillingDetails().setEmail("klient@example.com");

        // when / then
        assertThat(decisions.required(storeWithReceipts(PosReceiptMode.E_RECEIPT), order, OrderStatus.Delivered)).isEmpty();
    }

    @Test
    void doesNotAskOnceAnAttemptExists() {
        // given
        Order order = shippingPosOrder();
        when(attemptService.attemptsOf(STORE_ID, ORDER_ID)).thenReturn(List.of(new ReceiptAttempt()));

        // when / then
        assertThat(decisions.required(storeWithReceipts(PosReceiptMode.ASK), order, OrderStatus.Delivered)).isEmpty();
    }

    @Test
    void doesNotAskForOtherChannelsOtherStatusesOrWithReceiptsOff() {
        // given
        Order web = deliveredOrder(100);
        web.setStatus(OrderStatus.Shipping);
        Store off = storeWithReceipts(PosReceiptMode.ASK);
        off.getReceiptConfiguration().disable();

        // when / then
        assertThat(decisions.required(storeWithReceipts(PosReceiptMode.ASK), web, OrderStatus.Delivered)).isEmpty();
        assertThat(decisions.required(storeWithReceipts(PosReceiptMode.ASK), shippingPosOrder(), OrderStatus.Shipping)).isEmpty();
        assertThat(decisions.required(off, shippingPosOrder(), OrderStatus.Delivered)).isEmpty();
        assertThat(decisions.required(null, shippingPosOrder(), OrderStatus.Delivered)).isEmpty();
    }

    @Test
    void cashRegisterChoiceRecordsTheReceiptOnTheOrder() {
        // given
        Order order = shippingPosOrder();

        // when / then
        assertThat(decisions.apply(storeWithReceipts(PosReceiptMode.ASK), PosReceiptMode.ASK, order,
                form(PosReceiptMode.CASH_REGISTER, " 15/2026 ", null), TODAY)).isEmpty();

        // then
        assertThat(order.getDocuments()).singleElement().satisfies(d -> {
            assertThat(d.getType()).isEqualTo(DocumentType.Receipt);
            assertThat(d.getNumber()).isEqualTo("15/2026");
            assertThat(d.getIssuedAt()).isEqualTo(TODAY);
        });
        assertThat(order.isPosEReceiptRequested()).isFalse();
    }

    @Test
    void eReceiptChoiceStoresTheCustomersEmailAndTheChoice() {
        // given
        Order order = shippingPosOrder();

        // when / then
        assertThat(decisions.apply(storeWithReceipts(PosReceiptMode.ASK), PosReceiptMode.ASK, order,
                form(PosReceiptMode.E_RECEIPT, null, " klient@example.com "), TODAY)).isEmpty();

        // then
        assertThat(order.getBillingDetails().getEmail()).isEqualTo("klient@example.com");
        assertThat(order.isPosEReceiptRequested()).isTrue();
        assertThat(order.getDocuments()).isEmpty();
    }

    @Test
    void refusesABlankNumberAnInvalidEmailTheStoresEmailAMissingChoiceAndAChoiceTheModeForbids() {
        // given
        Store store = storeWithReceipts(PosReceiptMode.ASK);

        // when / then
        assertThat(decisions.apply(store, PosReceiptMode.ASK, shippingPosOrder(), form(PosReceiptMode.CASH_REGISTER, "   ", null), TODAY))
                .contains("receipts.pos.decision.numberRequired");
        assertThat(decisions.apply(store, PosReceiptMode.ASK, shippingPosOrder(), form(PosReceiptMode.E_RECEIPT, null, "klient"), TODAY))
                .contains("receipts.pos.decision.emailRequired");
        assertThat(decisions.apply(store, PosReceiptMode.ASK, shippingPosOrder(), form(PosReceiptMode.E_RECEIPT, null, STORE_EMAIL), TODAY))
                .contains("receipts.pos.decision.emailIsStores");
        assertThat(decisions.apply(store, PosReceiptMode.ASK, shippingPosOrder(), form(null, null, null), TODAY))
                .contains("receipts.pos.decision.choiceRequired");
        assertThat(decisions.apply(store, PosReceiptMode.CASH_REGISTER, shippingPosOrder(), form(PosReceiptMode.E_RECEIPT, null, "k@example.com"), TODAY))
                .contains("receipts.pos.decision.choiceRequired");
        assertThat(decisions.apply(store, PosReceiptMode.E_RECEIPT, shippingPosOrder(), form(PosReceiptMode.CASH_REGISTER, "1/2026", null), TODAY))
                .contains("receipts.pos.decision.choiceRequired");
        assertThat(decisions.apply(store, PosReceiptMode.ASK, shippingPosOrder(), form(PosReceiptMode.ASK, null, null), TODAY))
                .contains("receipts.pos.decision.choiceRequired");
        assertThat(decisions.apply(store, PosReceiptMode.ASK, shippingPosOrder(), null, TODAY))
                .contains("receipts.pos.decision.choiceRequired");
    }
}
