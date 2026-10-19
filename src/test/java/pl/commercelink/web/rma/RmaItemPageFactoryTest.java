package pl.commercelink.web.rma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.rma.*;
import pl.commercelink.web.dtos.RmaItemForm;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RmaItemPageFactoryTest {

    private static final Locale PL = Locale.forLanguageTag("pl");
    private RmaItemPageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        factory = new RmaItemPageFactory(messages);
    }

    private static RMA rma(RMAStatus status) {
        RMA rma = new RMA("store-1");
        rma.setRmaId("9f4e21b7-1111-2222-3333-444455556666");
        rma.setOrderId("3c0d9a41-aaaa-bbbb-cccc-ddddeeeeffff");
        rma.setStatus(status);
        ShippingDetails details = new ShippingDetails();
        details.setName("Jan");
        details.setSurname("Kowalski");
        details.setCity("Poznań");
        rma.setShippingDetails(details);
        return rma;
    }

    private static RMAItem item(RMAItemStatus status, String serialNo) {
        RMAItem item = new RMAItem();
        item.setRmaId("9f4e21b7-1111-2222-3333-444455556666");
        item.setRmaItemId("7c1e5a20-0000-0000-0000-000000000000");
        item.setItemId("oi-1");
        item.setName("RTX 4070");
        item.setQty(1);
        item.setPrice(2899.0);
        item.setCost(2150.0);
        item.setTax(1.23);
        item.setEan("4711377139183");
        item.setMfn("V513-610R");
        item.setStatus(status);
        item.setDesiredResolution(RMAResolutionType.Replacement);
        item.setSerialNo(serialNo);
        item.setDeliveryId("5a7c3e10-1111-2222-3333-444455556666");
        return item;
    }

    private static OrderItem orderItem(String serials) {
        OrderItem orderItem = new OrderItem();
        orderItem.setSerialNo(serials);
        return orderItem;
    }

    private RmaItemPage page(RMA rma, RMAItem item, OrderItem orderItem, Delivery delivery) {
        return factory.page("store-1", rma, item, orderItem, delivery, delivery == null ? null : "Action S.A.", null, Map.of(), null, null, PL);
    }

    @Test
    void headerShowsTheItemStatusWithItsToneAndTheRmaAsBack() {
        // when
        RmaItemPage page = page(rma(RMAStatus.ItemsReceived), item(RMAItemStatus.Received, "A1"), orderItem("A1"), null);

        // then
        assertThat(page.statusLabel()).isEqualTo("Otrzymany");
        assertThat(page.statusTone()).isEqualTo("is-warn");
        assertThat(page.backHref()).isEqualTo("/dashboard/rma/9f4e21b7-1111-2222-3333-444455556666");
        assertThat(page.backLabel()).isEqualTo("RMA 9f4e21b7");
        assertThat(page.shortId()).isEqualTo("7c1e5a20");
        assertThat(page.readOnly()).isFalse();
    }

    @Test
    void itemStatusTones() {
        // when / then
        assertThat(RmaItemPageFactory.tone(RMAItemStatus.New)).isEqualTo("is-info");
        assertThat(RmaItemPageFactory.tone(RMAItemStatus.Received)).isEqualTo("is-warn");
        assertThat(RmaItemPageFactory.tone(RMAItemStatus.SentForRepair)).isEqualTo("is-info");
        assertThat(RmaItemPageFactory.tone(RMAItemStatus.ReturnedToClient)).isEqualTo("is-ok");
        assertThat(RmaItemPageFactory.tone(RMAItemStatus.MovedToWarehouse)).isEqualTo("is-ok");
        assertThat(RmaItemPageFactory.tone(null)).isEqualTo("is-info");
    }

    @Test
    void undecidedResolutionIsNullNotTheFirstOption() {
        // when
        RmaItemPage page = page(rma(RMAStatus.New), item(RMAItemStatus.New, null), null, null);

        // then
        assertThat(page.resolution().desired()).isEqualTo("Wymiana");
        assertThat(page.resolution().actual()).isNull();
    }

    @Test
    void valueCardShowsGrossPriceNetCostVatAndTotal() {
        // given
        RMAItem item = item(RMAItemStatus.New, null);
        item.setQty(2);

        // when
        RmaItemPage page = page(rma(RMAStatus.New), item, null, null);

        // then
        assertThat(page.values()).extracting(RmaItemPage.Fact::value)
                .containsExactly("2 szt.", "2 899,00 PLN", "2 150,00 PLN", "23%");
        assertThat(page.total()).isEqualTo("5 798,00 PLN");
    }

    @Test
    void contextLinksTheRmaOrderDeliveryAndEachSerialHistory() {
        // given
        Delivery delivery = new Delivery();

        // when
        RmaItemPage page = page(rma(RMAStatus.ItemsReceived), item(RMAItemStatus.Received, "A1, A2"), orderItem("A1,A2"), delivery);

        // then
        RmaItemPage.Context c = page.context();
        assertThat(c.rmaShortId()).isEqualTo("9f4e21b7");
        assertThat(c.rmaStatusTone()).isEqualTo("is-warn");
        assertThat(c.source()).isEqualTo("Sklep");
        assertThat(c.client()).isEqualTo("Jan Kowalski · Poznań");
        assertThat(c.orderHref()).isEqualTo("/dashboard/orders/3c0d9a41-aaaa-bbbb-cccc-ddddeeeeffff");
        assertThat(c.orderShortId()).isEqualTo("3c0d9a41");
        assertThat(c.deliveryHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=5a7c3e10-1111-2222-3333-444455556666");
        assertThat(c.deliveryText()).isEqualTo("5a7c3e10");
        assertThat(c.deliverySupplier()).isEqualTo("Action S.A.");
        assertThat(c.serials()).extracting(s -> s.serialNo()).containsExactly("A1", "A2");
    }

    @Test
    void unknownDeliveryIsPlainTextAndUnknownIdHidesTheRow() {
        // given
        RMAItem unknown = item(RMAItemStatus.New, null);
        unknown.setDeliveryId("Unknown");

        // when
        RmaItemPage notFound = page(rma(RMAStatus.New), item(RMAItemStatus.New, null), null, null);
        RmaItemPage hidden = page(rma(RMAStatus.New), unknown, null, null);

        // then
        assertThat(notFound.context().deliveryHref()).isNull();
        assertThat(notFound.context().deliveryText()).isEqualTo("5a7c3e10");
        assertThat(hidden.context().deliveryText()).isNull();
    }

    @Test
    void marketplaceReturnNamesTheMarketplaceAndReturnReferenceAndEmailWithoutAddress() {
        // given
        RMA rma = rma(RMAStatus.WaitingForItems);
        rma.setShippingDetails(null);
        rma.setEmail("anna@example.com");
        rma.setMarketplace("Allegro");
        rma.setExternalReturnId("ret-1");
        rma.setExternalReturnReference("RT-5512-0931");

        // when
        RmaItemPage page = page(rma, item(RMAItemStatus.New, null), null, null);

        // then
        assertThat(page.context().source()).isEqualTo("Allegro · zwrot RT-5512-0931");
        assertThat(page.context().client()).isEqualTo("anna@example.com");
    }

    @Test
    void serialHintListsTheOrderNumbersWhenTheyMatch() {
        // when
        RmaItemPage page = page(rma(RMAStatus.New), item(RMAItemStatus.Received, "A1"), orderItem("A1,A2"), null);

        // then
        assertThat(page.serialHint().warn()).isFalse();
        assertThat(page.serialHint().text()).isEqualTo("Oddziel przecinkami. W zamówieniu: A1, A2.");
    }

    @Test
    void serialHintWarnsAboutANumberOutsideTheOrder() {
        // when
        RmaItemPage page = page(rma(RMAStatus.New), item(RMAItemStatus.Received, "A1, X9"), orderItem("A1,A2"), null);

        // then
        assertThat(page.serialHint().warn()).isTrue();
        assertThat(page.serialHint().text()).startsWith("Numeru X9 nie ma w pozycji zamówienia (w zamówieniu: A1, A2).");
    }

    @Test
    void serialHintIgnoresSpacingAndDuplicates() {
        // when
        RmaItemPage page = page(rma(RMAStatus.New), item(RMAItemStatus.Received, " A1 ,A1, "), orderItem("A1"), null);

        // then
        assertThat(page.serialHint().warn()).isFalse();
    }

    @Test
    void orderWithoutSerialsIsNotComparedAndMissingOrderItemGivesThePlainHint() {
        // when
        RmaItemPage noSerials = page(rma(RMAStatus.New), item(RMAItemStatus.Received, "X9"), orderItem(null), null);
        RmaItemPage noOrderItem = page(rma(RMAStatus.New), item(RMAItemStatus.Received, "X9"), null, null);

        // then
        assertThat(noSerials.serialHint().warn()).isFalse();
        assertThat(noSerials.serialHint().text()).isEqualTo("Oddziel przecinkami. W zamówieniu brak numerów seryjnych.");
        assertThat(noOrderItem.serialHint().text()).isEqualTo("Oddziel przecinkami.");
    }

    @Test
    void settledItemAddsThatTheChangeDoesNotReachWarehouseOrOrder() {
        // when
        RmaItemPage page = page(rma(RMAStatus.Processing), item(RMAItemStatus.MovedToWarehouse, "A1"), orderItem("A1"), null);

        // then
        assertThat(page.serialHint().text()).endsWith("Zmiana numeru nie poprawi go w magazynie ani w zamówieniu.");
    }

    @Test
    void closedRmaIsReadOnlyUnlessAPostIsBeingAnswered() {
        // given
        RMA rma = rma(RMAStatus.Completed);
        RMAItem item = item(RMAItemStatus.MovedToWarehouse, "A1");

        // when
        RmaItemPage shown = page(rma, item, null, null);
        RmaItemPage refused = factory.page("store-1", rma, item, null, null, null, RmaItemForm.of(item), Map.of(), "closed", null, PL);

        // then
        assertThat(shown.readOnly()).isTrue();
        assertThat(shown.closedTitle()).isEqualTo("RMA zakończone");
        assertThat(refused.readOnly()).isFalse();
        assertThat(refused.errorMessage()).isEqualTo("closed");
    }

    @Test
    void postedFormKeepsTheTypedValuesAndDrivesTheSerialHint() {
        // given
        RMAItem item = item(RMAItemStatus.Received, "A1");
        RmaItemForm posted = RmaItemForm.of(item);
        posted.setSerialNo("X9");

        // when
        RmaItemPage page = factory.page("store-1", rma(RMAStatus.New), item, orderItem("A1"), null, null, posted, Map.of(), null, null, PL);

        // then
        assertThat(page.form()).isSameAs(posted);
        assertThat(page.serialHint().warn()).isTrue();
    }
}
