package pl.commercelink.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V016_MoveMarketplaceShippingDateToPreferredTest {

    private static final String STORE_ID = "store-1";
    private static final LocalDateTime AFTER_CUTOFF = LocalDateTime.of(2026, 9, 3, 0, 0);
    private static final LocalDateTime BEFORE_CUTOFF = LocalDateTime.of(2026, 9, 2, 23, 59, 59);
    private static final LocalDate MARKETPLACE_DATE = LocalDate.of(2026, 9, 10);

    @Mock
    private StoresRepository storesRepository;
    @Mock
    private OrdersRepository ordersRepository;

    @InjectMocks
    private V016_MoveMarketplaceShippingDateToPreferred migration;

    @Test
    void marketplaceOrderPlacedBeforeTheCutoffIsLeftUntouched() {
        Order order = marketplaceOrder(BEFORE_CUTOFF, MARKETPLACE_DATE, null);
        givenOrders(order);

        migration.migrate();

        verify(ordersRepository, never()).save(any());
        assertThat(order.getPreferredShippingAt()).isNull();
        assertThat(order.getEstimatedShippingAt()).isEqualTo(MARKETPLACE_DATE);
    }

    @Test
    void orderNotImportedFromMarketplaceIsLeftUntouched() {
        Order order = order(AFTER_CUTOFF, MARKETPLACE_DATE, null);
        order.setSource(new OrderSource("Shop", OrderSourceType.Other));
        order.setExternalOrderId("ext-1");
        givenOrders(order);

        migration.migrate();

        verify(ordersRepository, never()).save(any());
        assertThat(order.getPreferredShippingAt()).isNull();
        assertThat(order.getEstimatedShippingAt()).isEqualTo(MARKETPLACE_DATE);
    }

    @Test
    void orderWithPreferredShippingDateAlreadySetIsLeftUntouched() {
        Order order = marketplaceOrder(AFTER_CUTOFF, MARKETPLACE_DATE, null);
        order.setPreferredShippingAt(LocalDate.of(2026, 9, 15));
        givenOrders(order);

        migration.migrate();

        verify(ordersRepository, never()).save(any());
        assertThat(order.getPreferredShippingAt()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(order.getEstimatedShippingAt()).isEqualTo(MARKETPLACE_DATE);
    }

    @Test
    void orderWithoutAnyShippingDateIsLeftUntouched() {
        Order order = marketplaceOrder(AFTER_CUTOFF, null, null);
        givenOrders(order);

        migration.migrate();

        verify(ordersRepository, never()).save(any());
        assertThat(order.getPreferredShippingAt()).isNull();
    }

    @Test
    void marketplaceDateIsMovedToPreferredWhenAssemblyWasNeverCalculated() {
        Order order = marketplaceOrder(AFTER_CUTOFF, MARKETPLACE_DATE, null);
        givenOrders(order);

        migration.migrate();

        verify(ordersRepository).save(order);
        assertThat(order.getPreferredShippingAt()).isEqualTo(MARKETPLACE_DATE);
        assertThat(order.getEstimatedShippingAt()).isNull();
    }

    @Test
    void marketplaceDateIsCopiedToPreferredAndCalculatedDateKeptWhenAssemblyIsSet() {
        LocalDate calculatedShippingAt = LocalDate.of(2026, 9, 8);
        Order order = marketplaceOrder(AFTER_CUTOFF, calculatedShippingAt, LocalDate.of(2026, 9, 7));
        givenOrders(order);

        migration.migrate();

        verify(ordersRepository).save(order);
        assertThat(order.getPreferredShippingAt()).isEqualTo(calculatedShippingAt);
        assertThat(order.getEstimatedShippingAt()).isEqualTo(calculatedShippingAt);
        assertThat(order.getEstimatedAssemblyAt()).isEqualTo(LocalDate.of(2026, 9, 7));
    }

    @Test
    void secondRunIsANoOp() {
        Order withoutAssembly = marketplaceOrder(AFTER_CUTOFF, MARKETPLACE_DATE, null);
        Order withAssembly = marketplaceOrder(AFTER_CUTOFF, MARKETPLACE_DATE, LocalDate.of(2026, 9, 7));
        givenOrders(withoutAssembly, withAssembly);

        migration.migrate();
        migration.migrate();

        verify(ordersRepository, times(1)).save(withoutAssembly);
        verify(ordersRepository, times(1)).save(withAssembly);
        assertThat(withoutAssembly.getPreferredShippingAt()).isEqualTo(MARKETPLACE_DATE);
        assertThat(withoutAssembly.getEstimatedShippingAt()).isNull();
        assertThat(withAssembly.getPreferredShippingAt()).isEqualTo(MARKETPLACE_DATE);
        assertThat(withAssembly.getEstimatedShippingAt()).isEqualTo(MARKETPLACE_DATE);
    }

    private void givenOrders(Order... orders) {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        when(storesRepository.findAll()).thenReturn(List.of(store));
        when(ordersRepository.findAll(STORE_ID)).thenReturn(List.of(orders));
    }

    private static Order marketplaceOrder(LocalDateTime orderedAt, LocalDate estimatedShippingAt, LocalDate estimatedAssemblyAt) {
        Order order = order(orderedAt, estimatedShippingAt, estimatedAssemblyAt);
        order.setSource(new OrderSource("Allegro", OrderSourceType.Marketplace));
        order.setExternalOrderId("mp-order-1");
        return order;
    }

    private static Order order(LocalDateTime orderedAt, LocalDate estimatedShippingAt, LocalDate estimatedAssemblyAt) {
        Order order = new Order(STORE_ID);
        order.setOrderedAt(orderedAt);
        order.setEstimatedShippingAt(estimatedShippingAt);
        order.setEstimatedAssemblyAt(estimatedAssemblyAt);
        return order;
    }
}
