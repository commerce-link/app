package pl.commercelink.web.itemhistory;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.history.*;
import pl.commercelink.orders.rma.RMAItemStatus;
import pl.commercelink.orders.rma.RMAResolutionType;
import pl.commercelink.warehouse.api.WarehouseItemView;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.commercelink.orders.history.ItemNowTest.at;
import static pl.commercelink.orders.history.ItemNowTest.order;
import static pl.commercelink.orders.history.ItemNowTest.rma;

class ItemHistoryPageFactoryTest {

    static final Locale PL = Locale.forLanguageTag("pl");
    final ItemHistoryPageFactory factory = new ItemHistoryPageFactory(messages());

    static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    void anEmptySearchIsTheSearchPage() {
        // when
        ItemHistoryPage page = factory.empty();

        // then
        assertThat(page.searched()).isFalse();
        assertThat(page.serialNo()).isNull();
    }

    @Test
    void rendersAnOrderEventWithItsPillAndClient() {
        // given
        OrderLine line = order("7f3a91c2-aaaa-bbbb", OrderStatus.Assembly, FulfilmentStatus.Reserved, at(9, 29));
        BillingDetails billing = new BillingDetails();
        billing.setName("Anna");
        billing.setSurname("Nowak");
        line.order().setBillingDetails(billing);
        line.item().setName("Samsung 980 PRO");
        ItemHistory history = history(List.of(line), List.of(), List.of(), List.of(eventOf(line)));

        // when
        ItemHistoryPage.Event event = factory.of(history, PL).events().get(0);

        // then
        assertThat(event.title()).isEqualTo("Zamówienie złożone");
        assertThat(event.recordText()).isEqualTo("Zamówienie 7f3a91c2");
        assertThat(event.recordHref()).isEqualTo("/dashboard/orders/7f3a91c2-aaaa-bbbb");
        assertThat(event.pillText()).isEqualTo("W kompletacji");
        assertThat(event.pillTone()).isEqualTo("is-info");
        assertThat(event.facts()).isEqualTo("Klient: Anna Nowak");
        assertThat(event.at()).isEqualTo("29.09.2026, 12:00");
    }

    @Test
    void rendersAnRmaEventWithoutStatusOrResolutionWithoutFailing() {
        // given
        RmaLine line = rma("3f2a9c10-x", null, null);

        // when
        ItemHistoryPage.Event event = factory.of(history(List.of(), List.of(line), List.of(), List.of(
                new ItemHistoryEvent(ItemHistoryEvent.Type.RMA_CREATED, null, null, line, null))), PL).events().get(0);

        // then
        assertThat(event.pillText()).isNull();
        assertThat(event.facts()).isNull();
        assertThat(event.at()).isNull();
        assertThat(event.recordHref()).isEqualTo("/dashboard/rma/3f2a9c10-x");
    }

    @Test
    void rendersAnRmaEventWithRequestedAndActualResolution() {
        // given
        RmaLine line = rma("rma-1", RMAItemStatus.MovedToWarehouse, at(9, 21));
        line.item().setDesiredResolution(RMAResolutionType.Return);
        line.item().setActualResolution(RMAResolutionType.Return);

        // when
        ItemHistoryPage.Event event = factory.of(history(List.of(), List.of(line), List.of(), List.of(
                new ItemHistoryEvent(ItemHistoryEvent.Type.RMA_CREATED, at(9, 21), null, line, null))), PL).events().get(0);

        // then
        assertThat(event.pillText()).isEqualTo("Przeniesiony do magazynu");
        assertThat(event.pillTone()).isEqualTo("is-neutral");
        assertThat(event.facts()).isEqualTo("Oczekiwane: Zwrot · Rozstrzygnięcie: Zwrot");
    }

    @Test
    void rendersADeliveryWithSupplierAndReferenceButNotAProvisionalOne() {
        // given
        Delivery delivery = new Delivery();
        delivery.setDeliveryId("2f9eb794-1");
        delivery.setProvider("Acme");
        delivery.setExternalDeliveryId("ZS/104733/2026");

        // when
        ItemHistoryPage.Event event = factory.of(history(List.of(), List.of(), List.of(), List.of(
                new ItemHistoryEvent(ItemHistoryEvent.Type.DELIVERY_ORDERED, at(8, 25), null, null, delivery))), PL).events().get(0);

        // then
        assertThat(event.title()).isEqualTo("Zamówiony u dostawcy");
        assertThat(event.recordHref()).isEqualTo("/dashboard/deliveries/details?deliveryId=2f9eb794-1");
        assertThat(event.facts()).isEqualTo("Dostawca: Acme · Nr u dostawcy: ZS/104733/2026");
    }

    @Test
    void warehouseOnlyItemWithoutNameOrConditionFallsBack() {
        // given
        WarehouseItemView stock = new WarehouseItemView("store-1", "w-1", null, null, null, null, 1, FulfilmentStatus.Delivered, null);

        // when
        ItemHistoryPage page = factory.of(history(List.of(), List.of(), List.of(stock), List.of()), PL);

        // then
        assertThat(page.product().title()).isEqualTo("Przedmiot bez nazwy");
        assertThat(page.now().label()).isEqualTo("W magazynie");
        assertThat(page.now().text()).isEqualTo("Na stanie magazynu");
        assertThat(page.now().href()).isEqualTo("/dashboard/warehouse/items/w-1");
        assertThat(page.emptyEventsText()).isEqualTo("Brak zdarzeń — przedmiot jest tylko w magazynie.");
    }

    @Test
    void anAmbiguousNumberCarriesTheWarningCollapsesTheTimelineAndSaysItIsTruncated() {
        // given
        List<OrderLine> lines = IntStream.range(0, 12).mapToObj(i -> {
            OrderLine line = order("o-" + i, OrderStatus.Completed, FulfilmentStatus.Delivered, at(9, 1 + i));
            line.item().setName("P" + (i % 2));
            line.item().setEan("590-" + (i % 2));
            return line;
        }).toList();
        List<ItemHistoryEvent> events = lines.stream().map(ItemHistoryPageFactoryTest::eventOf).toList();
        ItemIdentity identity = ItemIdentity.of(lines, List.of(), List.of());
        ItemHistory history = new ItemHistory("BRAK", true, identity, ItemAmbiguity.of(lines, identity),
                ItemNow.resolve(lines, List.of(), List.of()), events, 512);

        // when
        ItemHistoryPage page = factory.of(history, PL);

        // then
        assertThat(page.warningCounts()).isEqualTo("Zamówienia z tym numerem: 12 · Różne produkty: 2");
        assertThat(page.product().title()).startsWith("Różne produkty: 2 (najnowszy: ");
        assertThat(page.now().text()).endsWith(" · numer niejednoznaczny");
        assertThat(page.timelineLimit()).isEqualTo(10);
        assertThat(page.moreText()).isEqualTo("Pokaż 2 wcześniejsze zdarzenia");
        assertThat(page.eventsNote()).isEqualTo("Od najnowszego zdarzenia. Pokazano 12 najnowszych z 512.");
    }

    @Test
    void notFoundKeepsTheNumber() {
        // when
        ItemHistoryPage page = factory.of(new ItemHistory("SN-9", false, null, new ItemAmbiguity(false, 0, 0),
                ItemNow.resolve(List.of(), List.of(), List.of()), List.of(), 0), PL);

        // then
        assertThat(page.searched()).isTrue();
        assertThat(page.found()).isFalse();
        assertThat(page.serialNo()).isEqualTo("SN-9");
    }

    @Test
    void anItemInAnRmaIsNowInTheRma() {
        // given
        RmaLine line = rma("rma-1", RMAItemStatus.SentForRepair, at(9, 21));

        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(), List.of(line), List.of(), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("W reklamacji");
        assertThat(now.tone()).isEqualTo("is-warn");
        assertThat(now.text()).isEqualTo("RMA rma-1 · Wysłany do naprawy");
        assertThat(now.linkText()).isEqualTo("RMA");
        assertThat(now.href()).isEqualTo("/dashboard/rma/rma-1");
    }

    @Test
    void anItemInAnOpenOrderIsNowInTheOrder() {
        // given
        OrderLine line = order("o-1", OrderStatus.Assembly, FulfilmentStatus.Reserved, at(9, 29));

        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(line), List.of(), List.of(), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("W zamówieniu");
        assertThat(now.tone()).isEqualTo("is-info");
        assertThat(now.text()).isEqualTo("Zamówienie o-1 · W kompletacji · Pozycja: Zarezerwowany");
        assertThat(now.linkText()).isEqualTo("Zamówienie");
        assertThat(now.href()).isEqualTo("/dashboard/orders/o-1");
    }

    @Test
    void aDeliveredOrderMeansTheItemIsAtTheCustomer() {
        // given
        OrderLine line = order("o-1", OrderStatus.Delivered, FulfilmentStatus.Delivered, at(9, 2));

        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(line), List.of(), List.of(), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("U klienta");
        assertThat(now.tone()).isEqualTo("is-neutral");
        assertThat(now.text()).startsWith("Sprzedany w zamówieniu o-1 (Dostarczone) · ");
        assertThat(now.href()).isEqualTo("/dashboard/orders/o-1");
    }

    @Test
    void anAtCustomerOrderWithoutStatusDoesNotRenderEmptyParentheses() {
        // given
        OrderLine line = order("o-1", null, FulfilmentStatus.Delivered, at(9, 2));

        // when
        ItemHistoryPage.Now now = factory.of(new ItemHistory("SN-1", true, ItemIdentity.of(List.of(line), List.of(), List.of()),
                new ItemAmbiguity(false, 1, 1), new ItemNow(ItemNow.State.AT_CUSTOMER, line, null, null), List.of(), 0), PL).now();

        // then
        assertThat(now.text()).startsWith("Sprzedany w zamówieniu o-1 (Nieznany)");
        assertThat(now.text()).doesNotContain("()");
    }

    @Test
    void aReservedWarehouseRowIsReserved() {
        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(), List.of(), List.of(WAREHOUSE_ROW.apply(FulfilmentStatus.Reserved)), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("Zarezerwowany");
        assertThat(now.tone()).isEqualTo("is-info");
        assertThat(now.text()).isEqualTo("Zarezerwowany w magazynie · Stan: Otwarte opakowanie");
        assertThat(now.href()).isEqualTo("/dashboard/warehouse/items/w-Reserved");
    }

    @Test
    void anOrderedWarehouseRowIsInbound() {
        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(), List.of(), List.of(WAREHOUSE_ROW.apply(FulfilmentStatus.Ordered)), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("W drodze do magazynu");
        assertThat(now.tone()).isEqualTo("is-info");
        assertThat(now.text()).isEqualTo("Zamówiony u dostawcy, czeka na odbiór · Stan: Otwarte opakowanie");
        assertThat(now.href()).isEqualTo("/dashboard/warehouse/items/w-Ordered");
    }

    @Test
    void anOtherwiseStatedWarehouseRowUsesTheItemStatusAsLabel() {
        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(), List.of(), List.of(WAREHOUSE_ROW.apply(FulfilmentStatus.InRMA)), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("W reklamacji");
        assertThat(now.tone()).isEqualTo("is-warn");
        assertThat(now.text()).isEqualTo("Stan: Otwarte opakowanie");
        assertThat(now.href()).isEqualTo("/dashboard/warehouse/items/w-InRMA");
    }

    @Test
    void aWarehouseRowWithoutStatusIsLabelledUnknown() {
        // given
        WarehouseItemView stock = new WarehouseItemView("store-1", "w-1", "SSD", null, null, null, 1, null, null);

        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(), List.of(), List.of(stock), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("Nieznany");
        assertThat(now.tone()).isEqualTo("is-neutral");
        assertThat(now.href()).isEqualTo("/dashboard/warehouse/items/w-1");
    }

    @Test
    void anOtherwiseStatedWarehouseRowRendersInEnglish() {
        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(), List.of(), List.of(WAREHOUSE_ROW.apply(FulfilmentStatus.Destroyed)), List.of()), Locale.ENGLISH).now();

        // then
        assertThat(now.label()).isEqualTo("Destroyed");
        assertThat(now.tone()).isEqualTo("is-bad");
        assertThat(now.text()).isEqualTo("Condition: Open box");
    }

    @Test
    void anItemWithoutAnyLocationRecordIsUnknown() {
        // given
        RmaLine line = rma("rma-1", RMAItemStatus.MovedToWarehouse, at(9, 21));

        // when
        ItemHistoryPage.Now now = factory.of(history(List.of(), List.of(line), List.of(), List.of()), PL).now();

        // then
        assertThat(now.label()).isEqualTo("Nieznany");
        assertThat(now.tone()).isEqualTo("is-neutral");
        assertThat(now.text()).isEqualTo("Brak rekordu, który mówi, gdzie przedmiot jest teraz");
        assertThat(now.href()).isNull();
    }

    static final java.util.function.Function<FulfilmentStatus, WarehouseItemView> WAREHOUSE_ROW = ItemNowTest::warehouse;

    static ItemHistoryEvent eventOf(OrderLine line) {
        return new ItemHistoryEvent(ItemHistoryEvent.Type.ORDER_PLACED, line.placedAt(), line, null, null);
    }

    static ItemHistory history(List<OrderLine> orders, List<RmaLine> rmas, List<WarehouseItemView> stock, List<ItemHistoryEvent> events) {
        ItemIdentity identity = ItemIdentity.of(orders, rmas, stock);
        return new ItemHistory("SN-1", true, identity, ItemAmbiguity.of(orders, identity),
                ItemNow.resolve(orders, rmas, stock), events, events.size());
    }
}
