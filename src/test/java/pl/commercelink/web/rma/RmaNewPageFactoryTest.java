package pl.commercelink.web.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.rma.OpenRmaCoverage;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.orders.rma.RmaCreateService;
import pl.commercelink.orders.rma.RmaOrderSearch;
import pl.commercelink.web.dtos.RmaCreateForm;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RmaNewPageFactoryTest {

    private static final String STORE = "store-1";
    private static final Locale PL = Locale.forLanguageTag("pl");
    private static final String ORDER_ID = "8c1d0291-5b2e-4f7a-9d61-3e77d244a0c1";

    @Mock
    private OrderItemsRepository orderItems;
    @Mock
    private OpenRmaCoverage coverage;
    private RmaNewPageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        factory = new RmaNewPageFactory(orderItems, coverage, messages);
    }

    private static Order order(OrderStatus status) {
        Order order = new Order(STORE);
        order.setOrderId(ORDER_ID);
        order.setStatus(status);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("anna@x.pl");
        order.setBillingDetails(billing);
        order.setOrderedAt(LocalDateTime.of(2026, 10, 2, 9, 30));
        return order;
    }

    private static OrderItem item(String id, String serial, int qty) {
        OrderItem item = new OrderItem();
        item.setOrderId(ORDER_ID);
        item.setItemId(id);
        item.setName("Produkt " + id);
        item.setSerialNo(serial);
        item.setQty(qty);
        item.setPrice(100.0);
        item.setStatus(FulfilmentStatus.Delivered);
        return item;
    }

    @Test
    void aBlankSearchIsTheStartPage() {
        // when
        RmaNewPage page = factory.build(STORE, new RmaOrderSearch.Result(RmaOrderSearch.Kind.EMPTY, null, false, null, null, List.of(), false), null, PL);

        // then
        assertThat(page.state()).isEqualTo(RmaNewPage.State.START);
        assertThat(page.backHref()).isEqualTo("/dashboard/rma");
    }

    @Test
    void anOrderFoundBySerialTicksThatItemAndMarksAClaimedOne() {
        // given
        when(orderItems.findByOrderIdAndStatuses(ORDER_ID, RmaCreateService.REPORTABLE))
                .thenReturn(List.of(item("i1", "SN-1", 1), item("i2", null, 2)));
        when(coverage.claims(STORE, List.of("i1", "i2"))).thenReturn(Map.of("i2", "e3b0c442-9999"));
        RmaOrderSearch.Result found = new RmaOrderSearch.Result(RmaOrderSearch.Kind.ORDER, "SN-1", false, order(OrderStatus.Delivered), "i1", List.of(), false);

        // when
        RmaNewPage page = factory.build(STORE, found, "order", PL);

        // then
        assertThat(page.state()).isEqualTo(RmaNewPage.State.ORDER);
        assertThat(page.backHref()).isEqualTo("/dashboard/orders/" + ORDER_ID);
        assertThat(page.backLabel()).isEqualTo("Zamówienie 8c1d0291");
        assertThat(page.items()).extracting(RmaNewPage.ItemRow::selected).containsExactly(true, false);
        assertThat(page.items().get(0).resolution()).isNull();
        assertThat(page.items().get(1).claimedShortId()).isEqualTo("e3b0c442");
        assertThat(page.items().get(1).claimedHref()).isEqualTo("/dashboard/rma/e3b0c442-9999");
        assertThat(page.summary().email()).isEqualTo("anna@x.pl");
        assertThat(page.summary().count()).isEqualTo(1);
        assertThat(page.summary().canCreate()).isTrue();
    }

    @Test
    void aShippedOrderIsTooEarly() {
        // when
        RmaNewPage page = factory.build(STORE, new RmaOrderSearch.Result(RmaOrderSearch.Kind.ORDER, "8c1d0291", false, order(OrderStatus.Shipping), null, List.of(), false), null, PL);

        // then
        assertThat(page.state()).isEqualTo(RmaNewPage.State.INELIGIBLE);
        assertThat(page.ineligible().titleKey()).isEqualTo("rma.new.ineligible.title.early");
        assertThat(page.ineligible().orderHref()).isEqualTo("/dashboard/orders/" + ORDER_ID);
    }

    @Test
    void aChoiceGreysOutTheOrdersThatCannotHaveAnRma() {
        // given
        Order cancelled = order(OrderStatus.Cancelled);
        RmaOrderSearch.Result choice = new RmaOrderSearch.Result(RmaOrderSearch.Kind.CHOICE, "anna@x.pl", true, null, null,
                List.of(order(OrderStatus.Delivered), cancelled), true);

        // when
        RmaNewPage page = factory.build(STORE, choice, null, PL);

        // then
        assertThat(page.state()).isEqualTo(RmaNewPage.State.CHOICE);
        assertThat(page.choice().rows()).extracting(RmaNewPage.ChoiceRow::unavailableKey)
                .containsExactly(null, "rma.new.choice.unavailable.cancelled");
        assertThat(page.choice().rows().get(0).href()).isEqualTo("/dashboard/rma/new?q=" + ORDER_ID);
        assertThat(page.choice().rows().get(1).href()).isNull();
        assertThat(page.choice().limitText()).isEqualTo("Pokazano 50 najnowszych.");
    }

    @Test
    void aRejectedSubmitKeepsWhatWasTypedAndNamesTheItemInTheSummary() {
        // given
        when(orderItems.findByOrderIdAndStatuses(ORDER_ID, RmaCreateService.REPORTABLE)).thenReturn(List.of(item("i1", null, 2)));
        when(coverage.claims(STORE, List.of("i1"))).thenReturn(Map.of());
        RmaCreateForm.Item posted = new RmaCreateForm.Item();
        posted.setItemId("i1");
        posted.setSelected(true);
        posted.setQty("3");
        posted.setResolution(RMAResolutionType.Repair);
        posted.setReason("Piszczy");
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId(ORDER_ID);
        form.setEmail("anna@gmail");
        form.setItems(new ArrayList<>(List.of(posted)));
        RmaCreateService.Outcome outcome = new RmaCreateService.Outcome(null, null, List.of(
                new RmaCreateForm.Error("items0.qty", "rma.new.error.qty", new Object[]{2}, "Produkt i1"),
                new RmaCreateForm.Error("email", "rma.new.error.email.invalid", new Object[0], null)));

        // when
        RmaNewPage page = factory.rebuild(STORE, order(OrderStatus.Delivered), form, outcome, PL);

        // then
        assertThat(page.items().get(0).qty()).isEqualTo("3");
        assertThat(page.items().get(0).resolution()).isEqualTo("Repair");
        assertThat(page.items().get(0).reason()).isEqualTo("Piszczy");
        assertThat(page.summary().email()).isEqualTo("anna@gmail");
        assertThat(page.errorSummary()).containsExactly(
                Map.entry("items0.qty", "Produkt i1 — ilość: od 1 do 2 szt."),
                Map.entry("email", "E-mail klienta: wpisz poprawny adres"));
        assertThat(page.fieldErrors()).containsEntry("items0.qty", "ilość: od 1 do 2 szt.");
    }

    @Test
    void anItemLeftUntickedInARejectedSubmitKeepsQuantityOne() {
        // given
        when(orderItems.findByOrderIdAndStatuses(ORDER_ID, RmaCreateService.REPORTABLE)).thenReturn(List.of(item("i1", null, 2)));
        when(coverage.claims(STORE, List.of("i1"))).thenReturn(Map.of());
        RmaCreateForm.Item posted = new RmaCreateForm.Item();
        posted.setItemId("i1");
        RmaCreateForm form = new RmaCreateForm();
        form.setOrderId(ORDER_ID);
        form.setEmail("anna@x.pl");
        form.setItems(new ArrayList<>(List.of(posted)));
        RmaCreateService.Outcome outcome = new RmaCreateService.Outcome(null, "rma.new.error.noItems", List.of());

        // when
        RmaNewPage page = factory.rebuild(STORE, order(OrderStatus.Delivered), form, outcome, PL);

        // then
        assertThat(page.items().get(0).selected()).isFalse();
        assertThat(page.items().get(0).qty()).isEqualTo("1");
    }
}
