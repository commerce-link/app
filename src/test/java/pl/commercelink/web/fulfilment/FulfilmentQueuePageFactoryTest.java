package pl.commercelink.web.fulfilment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderIndexEntry;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.orders.fulfilment.FulfilmentType;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FulfilmentQueuePageFactoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 8);
    private static final Locale PL = Locale.forLanguageTag("pl");

    @Mock
    private StoresRepository storesRepository;

    private FulfilmentQueuePageFactory factory;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        factory = new FulfilmentQueuePageFactory(messages, storesRepository);
    }

    private static Order order(String id, FulfilmentType type, LocalDateTime orderedAt) {
        Order order = new Order("store-1");
        order.setOrderId(id);
        order.setStatus(OrderStatus.New);
        order.setOrderedAt(orderedAt);
        order.setFulfilmentType(type);
        ShippingDetails shipping = new ShippingDetails();
        shipping.setName("Anna");
        shipping.setSurname("Nowak");
        shipping.setCity("Kraków");
        order.setShippingDetails(shipping);
        BillingDetails billing = new BillingDetails();
        billing.setEmail("anna@example.com");
        order.setBillingDetails(billing);
        return order;
    }

    private static Map<String, Order> byId(Order... orders) {
        Map<String, Order> map = new HashMap<>();
        for (Order order : orders) {
            map.put(order.getOrderId(), order);
        }
        return map;
    }

    @Test
    void warehouseGroupHasRowsKindTotalsAndAdminLinks() {
        // given
        Order first = order("aaaaaaaa-1", FulfilmentType.WarehouseFulfilment, LocalDateTime.of(2026, 10, 2, 9, 14));
        Order second = order("bbbbbbbb-2", FulfilmentType.WarehouseFulfilment, LocalDateTime.of(2025, 12, 30, 18, 3));
        List<OrderIndexEntry> group = List.of(OrderIndexEntry.fromOrder(first), OrderIndexEntry.fromOrder(second));

        // when
        FulfilmentQueuePage page = factory.build(false, List.of("old-1"), group, byId(first, second),
                Map.of("aaaaaaaa-1", 4, "bbbbbbbb-2", 2), TODAY, PL);

        // then
        assertThat(page.kind()).isEqualTo(FulfilmentQueuePage.GroupKind.WAREHOUSE);
        assertThat(page.hasGroup()).isTrue();
        assertThat(page.emptyState()).isNull();
        assertThat(page.itemsTotal()).isEqualTo(6);
        assertThat(page.skippedCount()).isEqualTo(1);
        assertThat(page.skipOrderIds()).containsExactly("old-1", "aaaaaaaa-1", "bbbbbbbb-2");
        assertThat(page.postAction()).isEqualTo("/dashboard/orders/fulfilment");
        assertThat(page.storeName()).isNull();
        assertThat(page.rows()).extracting(FulfilmentQueueRow::href, FulfilmentQueueRow::orderedAtText, FulfilmentQueueRow::itemsToOrder)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("/dashboard/orders/aaaaaaaa-1", "02.10 09:14", 4),
                        org.assertj.core.groups.Tuple.tuple("/dashboard/orders/bbbbbbbb-2", "30.12.2025 18:03", 2));
        assertThat(page.rows().get(0).clientName()).isEqualTo("Anna Nowak");
        assertThat(page.rows().get(0).clientCity()).isEqualTo("Kraków");
        assertThat(page.rows().get(0).number()).isEqualTo(first.getShortenedOrderId());
    }

    @Test
    void dropshipGroupForSuperAdminCarriesStoreNameAndStoreScopedPaths() {
        // given
        Order only = order("cccccccc-3", FulfilmentType.DirectToConsumer, LocalDateTime.of(2026, 10, 7, 21, 8));
        Store store = new Store();
        store.setName("Sklep Demo");
        when(storesRepository.findById("store-1")).thenReturn(store);

        // when
        FulfilmentQueuePage page = factory.build(true, List.of(), List.of(OrderIndexEntry.fromOrder(only)), byId(only),
                Map.of("cccccccc-3", 1), TODAY, PL);

        // then
        assertThat(page.kind()).isEqualTo(FulfilmentQueuePage.GroupKind.DROPSHIP);
        assertThat(page.storeName()).isEqualTo("Sklep Demo");
        assertThat(page.postAction()).isEqualTo("/dashboard/store/store-1/orders/fulfilment");
        assertThat(page.rows().get(0).href()).isEqualTo("/dashboard/store/store-1/orders/cccccccc-3");
    }

    @Test
    void superAdminSeesStoreIdWhenTheStoreHasNoName() {
        // given
        Order only = order("dddddddd-4", FulfilmentType.DirectToConsumer, LocalDateTime.of(2026, 10, 7, 8, 0));
        when(storesRepository.findById("store-1")).thenReturn(null);

        // when
        FulfilmentQueuePage page = factory.build(true, List.of(), List.of(OrderIndexEntry.fromOrder(only)), byId(only),
                Map.of("dddddddd-4", 1), TODAY, PL);

        // then
        assertThat(page.storeName()).isEqualTo("store-1");
    }

    @Test
    void emptyGroupWithoutSkipsMeansNothingIsWaiting() {
        // when
        FulfilmentQueuePage page = factory.build(false, List.of(), List.of(), Map.of(), Map.of(), TODAY, PL);

        // then
        assertThat(page.hasGroup()).isFalse();
        assertThat(page.kind()).isNull();
        assertThat(page.emptyState()).isEqualTo(FulfilmentQueuePage.EmptyState.NONE_WAITING);
        assertThat(page.nothingWaiting()).isTrue();
        assertThat(page.allSkipped()).isFalse();
    }

    @Test
    void emptyGroupAfterSkipsMeansEverythingWasSkippedAndDuplicatesAreCountedOnce() {
        // when
        FulfilmentQueuePage page = factory.build(false, List.of("a", "b", "a"), List.of(), Map.of(), Map.of(), TODAY, PL);

        // then
        assertThat(page.emptyState()).isEqualTo(FulfilmentQueuePage.EmptyState.ALL_SKIPPED);
        assertThat(page.skippedCount()).isEqualTo(2);
        assertThat(page.skipOrderIds()).containsExactly("a", "b");
    }

    @Test
    void rowFallsBackToTheIndexEntryWhenTheOrderWasNotLoaded() {
        // given
        OrderIndexEntry entry = new OrderIndexEntry("store-1", "eeeeeeee-5", "x@example.com",
                LocalDateTime.of(2026, 10, 1, 7, 5), OrderStatus.New, null);

        // when
        FulfilmentQueuePage page = factory.build(false, List.of(), List.of(entry), Map.of(), Map.of(), TODAY, PL);

        // then
        assertThat(page.kind()).isEqualTo(FulfilmentQueuePage.GroupKind.DROPSHIP);
        FulfilmentQueueRow row = page.rows().get(0);
        assertThat(row.clientName()).isEqualTo("x@example.com");
        assertThat(row.orderedAtText()).isEqualTo("01.10 07:05");
        assertThat(row.itemsToOrder()).isZero();
        assertThat(row.dueText()).isNotBlank();
    }
}
