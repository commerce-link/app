package pl.commercelink.web.orders;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderReview;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.Shipment;
import pl.commercelink.orders.ShipmentType;
import pl.commercelink.orders.fulfilment.FulfilmentType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class OrderClosingChecklistTest {

    static final Locale PL = Locale.forLanguageTag("pl");

    static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    private static Order partlyDeliveredOrder() {
        Order order = new Order("store-1");
        order.setTotalPrice(13307);
        order.setBillingDetails(new BillingDetails());
        order.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));
        Shipment delivered = new Shipment(ShipmentType.Courier);
        delivered.setDeliveredAt(LocalDateTime.now());
        order.addShipment(delivered);
        order.addShipment(new Shipment(ShipmentType.Courier));
        order.addPayment(new Payment("R", "Jan", PaymentSource.BankTransfer, 6654, 0));
        return order;
    }

    private static Order freshOrder() {
        Order order = new Order("store-1");
        order.setBillingDetails(new BillingDetails());
        return order;
    }

    private static Order unpaidOrder() {
        Order order = new Order("store-1");
        order.setBillingDetails(new BillingDetails());
        order.setTotalPrice(10000);
        return order;
    }

    private static Order settledOrder() {
        Order order = new Order("store-1");
        order.setBillingDetails(new BillingDetails());
        order.setTotalPrice(10000);
        Shipment delivered = new Shipment(ShipmentType.Courier);
        delivered.setDeliveredAt(LocalDateTime.now());
        order.addShipment(delivered);
        order.addPayment(new Payment("R", "Jan", PaymentSource.BankTransfer, 10000, 0));
        order.addDocument(new Document("r", "PAR/1", null, DocumentType.Receipt));
        order.setReview(new OrderReview(OrderReviewStatus.Positive));
        return order;
    }

    private static Order reviewPendingOrder() {
        Order order = settledOrder();
        order.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));
        return order;
    }

    @Test
    void listsExactlyTheConditionsOfSettlingAndWhatIsMissing() {
        // when
        OrderClosingChecklist checklist = OrderClosingChecklist.of(partlyDeliveredOrder(), true, messages(), PL);

        // then
        assertThat(checklist.items()).extracting(OrderClosingChecklist.Item::text).containsExactly(
                "Przesyłki dostarczone: 1 z 2",
                "Brakuje wpłaty: 6 653,00 PLN",
                "Paragon — dodaj przyciskiem „Dodaj dokument”",
                "Brak dokumentu wydania (WZ)",
                "Opinia do zebrania — zamówienie nie zamknie się, dopóki nie zmienisz statusu opinii");
        assertThat(checklist.missing()).isEqualTo(5);
        assertThat(checklist.title(messages(), PL)).isEqualTo("Do zamknięcia brakuje: 5");
        assertThat(checklist.items().get(4).reviewDialog()).isTrue();
        assertThat(checklist.items().get(0).anchor()).isEqualTo("#przesylki");
    }

    @Test
    void aSettledOrderSaysItClosesItselfAndAnOrderWithoutShipmentsSaysWhy() {
        // given
        Order settled = new Order("store-1");
        settled.setBillingDetails(new BillingDetails());
        settled.addDocument(new Document("r", "PAR/1", null, DocumentType.Receipt));
        settled.setReview(new OrderReview(OrderReviewStatus.Positive));

        // when
        OrderClosingChecklist checklist = OrderClosingChecklist.of(settled, false, messages(), PL);

        // then
        assertThat(checklist.missing()).isZero();
        assertThat(checklist.title(messages(), PL)).isEqualTo("Wszystko rozliczone — zamówienie zamknie się przy najbliższym zapisie (np. wpłaty, opinii albo statusu).");
        assertThat(checklist.items().get(0).text()).isEqualTo("Brak przesyłek (odbiór osobisty lub wysyłka poza systemem)");
        assertThat(checklist.items()).extracting(OrderClosingChecklist.Item::text)
                .contains("Dokument zamykający: Paragon PAR/1", "Opinia: Pozytywna")
                .noneMatch(text -> text.contains("WZ"));
    }

    @Test
    void allDoneEqualsOrderIsSettledForEveryFixture() {
        // given — the fixtures the test already builds: fresh, partly delivered, unpaid, invoiced, review pending
        for (Order order : List.of(freshOrder(), partlyDeliveredOrder(), unpaidOrder(), settledOrder(), reviewPendingOrder())) {
            for (boolean goodsIssueRequired : new boolean[]{false, true}) {
                // when
                OrderClosingChecklist checklist = OrderClosingChecklist.of(order, goodsIssueRequired, messages(), PL);
                // then
                assertThat(checklist.allDone()).as(order.getOrderId() + " wz=" + goodsIssueRequired).isEqualTo(order.isSettled(goodsIssueRequired));
            }
        }
    }

    @Test
    void aConsumerReceiptTodoPointsToAddDocument() {
        // given
        Order consumer = unpaidOrder();
        Order business = unpaidOrder();
        BillingDetails company = new BillingDetails();
        company.setTaxId("5250000000");
        business.setBillingDetails(company);

        // when
        List<String> consumerTexts = OrderClosingChecklist.of(consumer, false, messages(), PL).items().stream()
                .map(OrderClosingChecklist.Item::text).toList();
        List<String> businessTexts = OrderClosingChecklist.of(business, false, messages(), PL).items().stream()
                .map(OrderClosingChecklist.Item::text).toList();

        // then
        assertThat(consumerTexts).contains("Paragon — dodaj przyciskiem „Dodaj dokument”");
        assertThat(businessTexts).contains("Do wystawienia: Faktura VAT");
    }

    @Test
    void theMissingAndOverpaidAmountsCarryTheSharedCurrency() {
        // given
        Order overpaid = unpaidOrder();
        overpaid.addPayment(new Payment("R", "Jan", PaymentSource.BankTransfer, 10001, 0));

        // when
        List<String> unpaidTexts = OrderClosingChecklist.of(unpaidOrder(), false, messages(), PL).items().stream()
                .map(OrderClosingChecklist.Item::text).toList();
        List<String> overpaidTexts = OrderClosingChecklist.of(overpaid, false, messages(), PL).items().stream()
                .map(OrderClosingChecklist.Item::text).toList();

        // then
        assertThat(unpaidTexts).contains("Brakuje wpłaty: 10 000,00 PLN");
        assertThat(overpaidTexts).contains("Nadpłata: 1,00 PLN");
    }

    @Test
    void anOrderWithoutShipmentsShowsNotApplicable() {
        // given
        Order order = settledOrder();
        order.setShipments(new java.util.ArrayList<>());

        // when
        OrderClosingChecklist checklist = OrderClosingChecklist.of(order, false, messages(), PL);

        // then: not ticked as done, yet it does not block closing either
        OrderClosingChecklist.Item shipments = checklist.items().get(0);
        assertThat(shipments.state()).isEqualTo(OrderClosingChecklist.State.NOT_APPLICABLE);
        assertThat(shipments.done()).isFalse();
        assertThat(shipments.text()).isEqualTo("Brak przesyłek (odbiór osobisty lub wysyłka poza systemem)");
        assertThat(checklist.missing()).isZero();
        assertThat(checklist.allDone()).isTrue();
    }

    @Test
    void aDropshipOrderWithoutShipmentsSaysTheSupplierShips() {
        // given
        Order order = unpaidOrder();
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);

        // when
        OrderClosingChecklist checklist = OrderClosingChecklist.of(order, false, messages(), PL);

        // then
        assertThat(checklist.items().get(0).state()).isEqualTo(OrderClosingChecklist.State.NOT_APPLICABLE);
        assertThat(checklist.items().get(0).text()).isEqualTo("Przesyłki: nada je dostawca");
    }

    @Test
    void aReviewNotCollectedIsNotApplicable() {
        // given
        Order order = settledOrder();
        order.setReview(null);

        // when
        OrderClosingChecklist checklist = OrderClosingChecklist.of(order, false, messages(), PL);

        // then
        OrderClosingChecklist.Item review = checklist.items().get(checklist.items().size() - 1);
        assertThat(review.state()).isEqualTo(OrderClosingChecklist.State.NOT_APPLICABLE);
        assertThat(review.text()).isEqualTo("Opinia nie jest zbierana");
        assertThat(checklist.allDone()).isTrue();
    }
}
