package pl.commercelink.web.orders;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.documents.Document;
import pl.commercelink.documents.DocumentType;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderReview;
import pl.commercelink.orders.OrderReviewStatus;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.web.orders.OrderRow.DocMark;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class OrderRowMapperTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private OrderRowMapper mapper;
    private OrderRowMapper withWarehouseDocuments;

    @BeforeEach
    void mapper() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        mapper = new OrderRowMapper(messages, new Locale("pl"), false);
        withWarehouseDocuments = new OrderRowMapper(messages, new Locale("pl"), true);
    }

    private static Order order() {
        Order order = new Order("store-1");
        order.setOrderId("ab58c563-9f1e-4d2a-b0c1-000000000000");
        order.setStatus(OrderStatus.Blocked);
        order.setOrderedAt(LocalDateTime.of(2026, 9, 23, 10, 0));
        order.setEstimatedShippingAt(TODAY.minusDays(2));
        order.setTotalPrice(1249);
        Payment payment = new Payment(PaymentSource.OnlinePayment);
        payment.setAmount(0);
        order.setPayments(List.of(payment));
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Anna");
        shipping.setSurname("Nowak");
        shipping.setCity("Kraków");
        order.setShippingDetails(shipping);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("anna.nowak74@test.com");
        order.setBillingDetails(billing);
        order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
        order.setExternalOrderId("5749922740");
        return order;
    }

    @Test
    void marketplaceOrderOverdueAndUnpaid() {
        OrderRow row = mapper.map(order(), TODAY);
        assertThat(row.href()).isEqualTo("/dashboard/orders/ab58c563-9f1e-4d2a-b0c1-000000000000");
        assertThat(mapper.map(order(), TODAY, "/dashboard/orders?status=New").href())
                .isEqualTo("/dashboard/orders/ab58c563-9f1e-4d2a-b0c1-000000000000?returnTo=%2Fdashboard%2Forders%3Fstatus%3DNew");
        assertThat(row.number()).isEqualTo(order().getShortenedOrderId());
        assertThat(row.sourceText()).isEqualTo("Allegro");
        assertThat(row.externalId()).isEqualTo("nr 5749922740");
        assertThat(row.clientName()).isEqualTo("Anna Nowak");
        assertThat(row.clientCity()).isEqualTo("Kraków");
        assertThat(row.email()).isEqualTo("anna.nowak74@test.com");
        assertThat(row.dueText()).isEqualTo("23.09");
        assertThat(row.dueNote()).isEqualTo("po terminie: 2 dni");
        assertThat(row.dueTone()).isEqualTo("is-bad");
        assertThat(row.statusLabel()).isEqualTo("Zablokowane");
        assertThat(row.statusTone()).isEqualTo("is-bad");
        assertThat(row.totalText()).isEqualTo("1 249,00 PLN");
        assertThat(row.unpaidText()).isEqualTo("do zapłaty 1 249,00 PLN");
    }

    @Test
    void webStoreOrderDueTodayPaidCompanyClient() {
        Order order = order();
        order.setStatus(OrderStatus.Assembly);
        order.setSource(new OrderSource(null, OrderSourceType.WebStore));
        order.setExternalOrderId(null);
        order.setEstimatedShippingAt(TODAY);
        order.getPayments().get(0).setAmount(1249);
        order.getShippingDetails().setCompanyName("Nowak Sp. z o.o.");
        OrderRow row = mapper.map(order, TODAY);
        assertThat(row.sourceText()).isEqualTo("Sklep internetowy");
        assertThat(row.externalId()).isNull();
        assertThat(row.clientName()).isEqualTo("Nowak Sp. z o.o.");
        assertThat(row.dueNote()).isEqualTo("dziś");
        assertThat(row.dueTone()).isEqualTo("is-warn");
        assertThat(row.statusTone()).isEqualTo("is-info");
        assertThat(row.unpaidText()).isNull();
    }

    @Test
    void oneDayOverdueUsesTheSingularKey() {
        Order order = order();
        order.setEstimatedShippingAt(TODAY.minusDays(1));
        assertThat(mapper.map(order, TODAY).dueNote()).isEqualTo("po terminie: 1 dzień");
    }

    @Test
    void shippedOrderHasNoDueNoteAndOtherYearShowsTheYear() {
        Order order = order();
        order.setStatus(OrderStatus.Shipping);
        order.setEstimatedShippingAt(LocalDate.of(2025, 12, 30));
        OrderRow row = mapper.map(order, TODAY);
        assertThat(row.dueText()).isEqualTo("30.12.2025");
        assertThat(row.dueNote()).isNull();
        assertThat(row.dueTone()).isEmpty();
        assertThat(row.statusTone()).isEqualTo("is-info");
        assertThat(OrderRowMapper.statusTone(OrderStatus.Delivered)).isEqualTo("is-ok");
        assertThat(OrderRowMapper.statusTone(OrderStatus.Completed)).isEqualTo("is-ok");
        assertThat(OrderRowMapper.statusTone(OrderStatus.Cancelled)).isEqualTo("is-neutral");
    }

    @Test
    void bareOrderWithoutAddressesOrDateStillMaps() {
        Order bare = new Order("store-1");
        bare.setOrderId("x1");
        bare.setStatus(OrderStatus.New);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("only@mail.com");
        bare.setBillingDetails(billing);
        bare.setPayments(List.of());
        OrderRow row = mapper.map(bare, TODAY);
        assertThat(row.clientName()).isEqualTo("only@mail.com");
        assertThat(row.email()).isNull();
        assertThat(row.clientCity()).isNull();
        assertThat(row.dueText()).isEqualTo("bez terminu");
        assertThat(row.dueNote()).isNull();
        assertThat(row.sourceText()).isEqualTo("Inne");
        Order noEmail = new Order("store-1");
        noEmail.setOrderId("x2");
        noEmail.setStatus(OrderStatus.New);
        noEmail.setPayments(List.of());
        assertThat(mapper.map(noEmail, TODAY).clientName()).isEqualTo("(bez nazwy)");
    }

    /** A delivered order closes on its own only with a WZ, a closing document and a review asked for (Order.isSettled). */
    @Test
    void deliveredOrderMissingEverythingShowsThreeToDoMarks() {
        Order order = order();
        order.setStatus(OrderStatus.Delivered);
        order.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));

        OrderRow row = withWarehouseDocuments.map(order, TODAY);

        assertThat(row.marks()).extracting(DocMark::kind, DocMark::code, DocMark::state).containsExactly(
                tuple("wz", "WZ", "is-todo"), tuple("invoice", "PAR", "is-todo"), tuple("review", "", "is-todo"));
        assertThat(row.marks()).extracting(DocMark::label).containsExactly(
                "WZ: do wystawienia — blokuje zakończenie zamówienia",
                "Paragon: do wystawienia — blokuje zakończenie zamówienia",
                "Opinia: do zebrania — blokuje zakończenie zamówienia");
        assertThat(row.hasTodo()).isTrue();
    }

    /** Before delivery a gap is not work yet: only what already exists shows, and it starts at the left. */
    @Test
    void beforeDeliveryOnlyWhatExistsIsShown() {
        Order order = order();
        order.setStatus(OrderStatus.Realization);
        order.setReview(new OrderReview(OrderReviewStatus.ToBeCollected));
        assertThat(withWarehouseDocuments.map(order, TODAY).marks()).isEmpty();

        order.addDocument(new Document("d1", "FV/7/2026", null, DocumentType.InvoiceVat));
        OrderRow row = withWarehouseDocuments.map(order, TODAY);
        assertThat(row.marks()).extracting(DocMark::kind, DocMark::code, DocMark::state).containsExactly(tuple("invoice", "FV", "is-done"));
        assertThat(row.hasTodo()).isFalse();
    }

    @Test
    void issuedDocumentsAndAnsweredReviewAreDone() {
        Order order = order();
        order.setStatus(OrderStatus.Delivered);
        order.addDocument(new Document("d1", "WZ/5/2026", null, DocumentType.GoodsIssue));
        order.addDocument(new Document("d2", "FV/12/2026", null, DocumentType.InvoiceVat));
        order.setReview(new OrderReview(OrderReviewStatus.Positive));

        OrderRow row = mapper.map(order, TODAY);

        assertThat(row.marks()).extracting(DocMark::code, DocMark::state)
                .containsExactly(tuple("WZ", "is-done"), tuple("FV", "is-done"), tuple("", "is-done"));
        assertThat(row.marks()).extracting(DocMark::label).containsExactly(
                "WZ: wystawiono WZ/5/2026", "Faktura VAT: wystawiono FV/12/2026", "Opinia: Pozytywna");
        assertThat(row.hasTodo()).isFalse();
    }

    @Test
    void marksThatDoNotApplyAreLeftOut() {
        Order order = order();
        order.setStatus(OrderStatus.Delivered);
        order.setFulfilmentType(FulfilmentType.DirectToConsumer);
        order.setReview(new OrderReview(OrderReviewStatus.NotApplicable));
        assertThat(withWarehouseDocuments.map(order, TODAY).marks()).extracting(DocMark::kind).containsExactly("invoice");

        order.setFulfilmentType(FulfilmentType.WarehouseFulfilment);
        assertThat(mapper.map(order, TODAY).marks()).extracting(DocMark::kind)
                .as("a store without warehouse documents expects no WZ").containsExactly("invoice");

        order.setReview(new OrderReview(OrderReviewStatus.InProgress));
        assertThat(mapper.map(order, TODAY).marks()).extracting(DocMark::kind)
                .as("a review already requested no longer holds the order open").containsExactly("invoice");

        order.setReview(null);
        assertThat(mapper.map(order, TODAY).marks()).extracting(DocMark::kind).containsExactly("invoice");
    }

    private static Order companyOrderWithAdvanceInvoice(OrderStatus status) {
        Order order = order();
        order.setStatus(status);
        order.getBillingDetails().setCompanyName("Nowak IT sp. z o.o.");
        order.getBillingDetails().setTaxId("6762461234");
        order.addDocument(new Document("d1", "ZAM/2/2026", null, DocumentType.Order));
        order.addDocument(new Document("d2", "FZ/3/2026", null, DocumentType.InvoiceAdvance));
        return order;
    }

    /** An advance invoice is information, not a gap: a green FZ before delivery, nothing that holds the order open. */
    @Test
    void advanceInvoiceBeforeDeliveryShowsDoneAdvanceMarkOnly() {
        // given
        Order order = companyOrderWithAdvanceInvoice(OrderStatus.Realization);

        // when
        OrderRow row = mapper.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::kind, DocMark::code, DocMark::state)
                .containsExactly(tuple("advance", "FZ", "is-done"));
        assertThat(row.marks()).extracting(DocMark::label).containsExactly("Faktura zaliczkowa: wystawiono FZ/3/2026");
        assertThat(row.hasTodo()).as("the phone card shows nothing for a lone FZ").isFalse();
    }

    @Test
    void deliveredOrderWithAdvanceInvoiceAwaitsFinalInvoice() {
        // given
        Order order = companyOrderWithAdvanceInvoice(OrderStatus.Delivered);

        // when
        OrderRow row = mapper.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::kind, DocMark::code, DocMark::state).containsExactly(
                tuple("advance", "FZ", "is-done"), tuple("invoice", "FK", "is-todo"));
        assertThat(row.marks()).extracting(DocMark::label).containsExactly(
                "Faktura zaliczkowa: wystawiono FZ/3/2026",
                "Faktura końcowa: do wystawienia — blokuje zakończenie zamówienia");
        assertThat(row.hasTodo()).isTrue();
    }

    /** The final invoice always settles the advance one, so FK alone says both were issued and the row does not grow. */
    @Test
    void finalInvoiceReplacesAdvanceMark() {
        // given
        Order order = companyOrderWithAdvanceInvoice(OrderStatus.Delivered);
        order.addDocument(new Document("d3", "FK/12/2026", null, DocumentType.InvoiceFinal));

        // when
        OrderRow row = mapper.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::kind, DocMark::code, DocMark::state)
                .containsExactly(tuple("invoice", "FK", "is-done"));
        assertThat(row.marks()).extracting(DocMark::label).containsExactly("Faktura końcowa: wystawiono FK/12/2026");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"New", "Blocked", "Cancelled"})
    void advanceInvoiceIsShownInAnyStatusAndNeverBlocks(OrderStatus status) {
        // given
        Order order = companyOrderWithAdvanceInvoice(status);

        // when
        OrderRow row = mapper.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::kind, DocMark::state)
                .containsExactly(tuple("advance", "is-done"));
    }

    @Test
    void personalInvoiceKeepsInvoiceCode() {
        // given
        Order order = order();
        order.addDocument(new Document("d1", "FV/8/2026", null, DocumentType.InvoicePersonal));

        // when
        OrderRow row = mapper.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::code).containsExactly("FV");
    }

    @Test
    void receiptKeepsReceiptCode() {
        // given
        Order order = order();
        order.addDocument(new Document("d1", "PAR/9/2026", null, DocumentType.Receipt));

        // when
        OrderRow row = mapper.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::code).containsExactly("PAR");
    }

    @Test
    void advanceInvoiceWithoutNumberStillShowsItsMark() {
        // given
        Order order = order();
        order.addDocument(new Document("d1", null, null, DocumentType.InvoiceAdvance));

        // when
        OrderRow row = mapper.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::kind, DocMark::code)
                .containsExactly(tuple("advance", "FZ"));
    }

    @Test
    void englishCodesForAdvanceAndFinalInvoice() {
        // given
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        OrderRowMapper english = new OrderRowMapper(messages, Locale.ENGLISH, false);
        Order order = companyOrderWithAdvanceInvoice(OrderStatus.Delivered);

        // when
        OrderRow row = english.map(order, TODAY);

        // then
        assertThat(row.marks()).extracting(DocMark::code).containsExactly("ADV", "FIN");
        assertThat(row.marks()).extracting(DocMark::label).containsExactly(
                "Advance invoice: issued FZ/3/2026",
                "Final invoice: to be issued — blocks closing the order");
    }
}
