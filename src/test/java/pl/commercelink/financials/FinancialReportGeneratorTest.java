package pl.commercelink.financials;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.deliveries.DeliveriesRepository;
import pl.commercelink.inventory.deliveries.Delivery;
import pl.commercelink.orders.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialReportGeneratorTest {

    private static final String STORE = "s1";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 3);

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private DeliveriesRepository deliveriesRepository;
    @InjectMocks
    private FinancialReportGenerator generator;

    private final List<Order> orders = new ArrayList<>();
    private final Map<String, List<OrderItem>> items = new LinkedHashMap<>();

    @BeforeEach
    void stubBatchReads() {
        lenient().when(ordersRepository.findByIds(eq(STORE), anyList())).thenAnswer(call -> {
            List<String> ids = call.getArgument(1);
            return orders.stream().filter(o -> ids.contains(o.getOrderId())).toList();
        });
        lenient().when(orderItemsRepository.findByOrderIds(anyList())).thenAnswer(call -> {
            List<String> ids = call.getArgument(0);
            Map<String, List<OrderItem>> chunk = new LinkedHashMap<>();
            ids.forEach(id -> chunk.put(id, items.getOrDefault(id, List.of())));
            return chunk;
        });
    }

    @Test
    void splitsSalesByDayAndChannelWithAnEntryForEveryDayOfTheRange() {
        // given
        order("o1", LocalDateTime.of(2026, 9, 1, 10, 0), false, 123, 80);
        order("o2", LocalDateTime.of(2026, 9, 1, 12, 0), true, 246, 160);
        order("o3", LocalDateTime.of(2026, 9, 3, 9, 0), false, 123, 80);
        when(ordersRepository.findAllPastOrders(FROM, TO, STORE)).thenReturn(List.of(
                index("o1"), index("o2"), index("o3")));
        when(deliveriesRepository.findAll(eq(STORE), any(), any())).thenReturn(List.of());

        // when
        FinancialReports reports = generator.generate(STORE, FROM, TO);

        // then
        assertThat(reports.days()).extracting(DailySales::day)
                .containsExactly(FROM, FROM.plusDays(1), TO);
        DailySales first = reports.days().get(0);
        assertThat(first.ownOrders()).isEqualTo(1);
        assertThat(first.ownPriceNet()).isCloseTo(100, within(0.01));
        assertThat(first.ownProfitNet()).isCloseTo(20, within(0.01));
        assertThat(first.marketplaceOrders()).isEqualTo(1);
        assertThat(first.marketplacePriceNet()).isCloseTo(200, within(0.01));
        assertThat(reports.days().get(1).ownOrders() + reports.days().get(1).marketplaceOrders()).isZero();
        assertThat(reports.days().stream().mapToDouble(DailySales::ownPriceNet).sum())
                .isCloseTo(reports.ownSources().getTotalPrice(), within(0.01));
    }

    @Test
    void groupsDeliveriesBySupplierAndKeepsTheOnesWithoutASupplierLast() {
        // given
        when(ordersRepository.findAllPastOrders(FROM, TO, STORE)).thenReturn(List.of());
        when(deliveriesRepository.findAll(eq(STORE), any(), any())).thenReturn(List.of(
                delivery("AB", 10, 1), delivery("Action", 15, 0), delivery("Action", 5, 2.5), delivery(null, 7, 0)));

        // when
        FinancialReports reports = generator.generate(STORE, FROM, TO);

        // then
        assertThat(reports.suppliers()).containsExactly(
                new SupplierDeliveries("Action", 2, 20, 2.5),
                new SupplierDeliveries("AB", 1, 10, 1),
                new SupplierDeliveries(null, 1, 7, 0));
        assertThat(reports.suppliers().stream().mapToDouble(SupplierDeliveries::shippingCost).sum())
                .isEqualTo(reports.ownSources().getTotalShippingCost());
    }

    @Test
    void readsOrdersAndItemsInBatchesNeverOneByOne() {
        // given
        order("o1", LocalDateTime.of(2026, 9, 1, 10, 0), false, 123, 80);
        when(ordersRepository.findAllPastOrders(FROM, TO, STORE)).thenReturn(List.of(index("o1")));
        when(deliveriesRepository.findAll(eq(STORE), any(), any())).thenReturn(List.of());

        // when
        FinancialReports reports = generator.generate(STORE, FROM, TO);

        // then
        assertThat(reports.ownSources().getTotalNumberOfOrders()).isEqualTo(1);
        verify(ordersRepository, never()).findById(any(), any());
        verify(orderItemsRepository, never()).findByOrderId(any());
    }

    @Test
    void countsEveryOrderWhenTheItemsAreReadInSeveralChunks() {
        // given
        List<OrderIndexEntry> index = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            order("o" + i, LocalDateTime.of(2026, 9, 1, 10, 0), false, 123, 80);
            index.add(index("o" + i));
        }
        when(ordersRepository.findAllPastOrders(FROM, TO, STORE)).thenReturn(index);
        when(deliveriesRepository.findAll(eq(STORE), any(), any())).thenReturn(List.of());

        // when
        FinancialReports reports = generator.generate(STORE, FROM, TO);

        // then
        assertThat(reports.ownSources().getTotalNumberOfOrders()).isEqualTo(60);
        assertThat(reports.ownSources().getTotalPrice()).isCloseTo(6000, within(0.01));
        assertThat(reports.days().get(0).ownOrders()).isEqualTo(60);
    }

    @Test
    void skipsAnIndexEntryWhoseOrderIsNoLongerFound() {
        // given
        order("o1", LocalDateTime.of(2026, 9, 1, 10, 0), false, 123, 80);
        order("o3", LocalDateTime.of(2026, 9, 1, 12, 0), false, 123, 80);
        when(ordersRepository.findAllPastOrders(FROM, TO, STORE)).thenReturn(List.of(
                index("o1"), index("gone"), index("o3")));
        when(deliveriesRepository.findAll(eq(STORE), any(), any())).thenReturn(List.of());

        // when
        FinancialReports reports = generator.generate(STORE, FROM, TO);

        // then
        assertThat(reports.ownSources().getTotalNumberOfOrders()).isEqualTo(2);
        assertThat(reports.ownSources().getTotalPrice()).isCloseTo(200, within(0.01));
    }

    @Test
    void failsTheWholeReportWhenAnItemsChunkFails() {
        // given
        order("o1", LocalDateTime.of(2026, 9, 1, 10, 0), false, 123, 80);
        when(ordersRepository.findAllPastOrders(FROM, TO, STORE)).thenReturn(List.of(index("o1")));
        IllegalStateException failure = new IllegalStateException("dynamo down");
        when(orderItemsRepository.findByOrderIds(anyList())).thenThrow(failure);

        // when / then
        assertThatThrownBy(() -> generator.generate(STORE, FROM, TO)).isSameAs(failure);
    }

    @Test
    void asksForDeliveriesUpToTheLastInstantOfTheEndDay() {
        // given
        when(ordersRepository.findAllPastOrders(FROM, TO, STORE)).thenReturn(List.of());
        when(deliveriesRepository.findAll(eq(STORE), any(), any())).thenReturn(List.of());

        // when
        generator.generate(STORE, FROM, TO);

        // then
        verify(deliveriesRepository).findAll(STORE, FROM.atStartOfDay(), TO.atTime(java.time.LocalTime.MAX));
    }

    private void order(String id, LocalDateTime orderedAt, boolean marketplace, double grossPrice, double netCost) {
        Order order = new Order();
        order.setOrderId(id);
        order.setOrderedAt(orderedAt);
        if (marketplace) {
            order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
            order.setExternalOrderId("ext-" + id);
        } else {
            order.setSource(new OrderSource("Sklep", OrderSourceType.WebStore));
        }
        OrderItem item = new OrderItem(id, "GPU", "Card", 1, grossPrice, "sku", false);
        item.setCost(netCost);
        orders.add(order);
        items.put(id, List.of(item));
    }

    private OrderIndexEntry index(String id) {
        return new OrderIndexEntry(STORE, id, "a@b.pl", LocalDateTime.of(2026, 9, 1, 0, 0), OrderStatus.Completed, null);
    }

    private Delivery delivery(String provider, double shipping, double payment) {
        Delivery delivery = new Delivery();
        delivery.setProvider(provider);
        delivery.setShippingCost(shipping);
        delivery.setPaymentCost(payment);
        return delivery;
    }
}
