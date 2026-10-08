package pl.commercelink.orders.fulfilment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.Inventory;
import pl.commercelink.inventory.InventoryView;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Items for which no offer survives the generator are listed on the form instead of vanishing (spec D7). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualOrderFulfilmentUnmatchedTest {

    private static final String STORE_ID = "store-1";

    @Mock
    private Inventory inventory;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderLifecycle orderLifecycle;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private WarehouseFulfilmentService warehouseFulfilmentService;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private InventoryView inventoryView;

    private ManualOrderFulfilment service() {
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        return new ManualOrderFulfilment(inventory, ordersRepository, orderLifecycle, orderItemsRepository,
                warehouseFulfilmentService, supplierRegistry, storesRepository);
    }

    // an item without a SKU has no inventory candidate at all: the generator drops it before grouping
    private static OrderItem itemWithoutSku(String orderId, String name) {
        return new OrderItem(orderId, "Keyboards", name, 2, 449.0, null, false);
    }

    @Test
    void anItemNoOfferCoversIsListedAsUnmatched() {
        // given
        OrderItem keyboard = itemWithoutSku("order-1", "Logitech MX Keys S");
        when(orderItemsRepository.findByOrderIdAndStatus("order-1", FulfilmentStatus.New)).thenReturn(List.of(keyboard));

        // when
        FulfilmentForm form = service().init(STORE_ID, List.of("order-1"), "default", false, false, false);

        // then
        assertThat(form.getEntries()).isEmpty();
        assertThat(form.getUnmatched()).containsExactly(new UnmatchedItem("order-1", keyboard.getItemId(),
                "Logitech MX Keys S", 2, 449.0, UnmatchedItem.Reason.NO_OFFER));
    }

    @Test
    void withANarrowingOnTheReasonSaysTheNarrowingMayHaveRemovedTheOffer() {
        // given
        OrderItem keyboard = itemWithoutSku("order-1", "Logitech MX Keys S");
        when(orderItemsRepository.findByOrderIdAndStatus("order-1", FulfilmentStatus.New)).thenReturn(List.of(keyboard));

        // when
        FulfilmentForm form = service().init(STORE_ID, List.of("order-1"), "default", false, false, true);

        // then
        assertThat(form.getUnmatched()).extracting(UnmatchedItem::reason).containsExactly(UnmatchedItem.Reason.NARROWED);
    }

    @Test
    void aServiceIsMatchedByTheWarehouseAndIsNotListed() {
        // given
        OrderItem service = new OrderItem("order-1", "Services", "Montaż", 1, 99.0, null, false);
        service.setService(true);
        when(orderItemsRepository.findByOrderIdAndStatus("order-1", FulfilmentStatus.New)).thenReturn(List.of(service));

        // when
        FulfilmentForm form = service().init(STORE_ID, List.of("order-1"), "default", false, false, false);

        // then
        assertThat(form.getUnmatched()).isEmpty();
    }

    @Test
    void ordersWithoutItemsToOrderGiveNoUnmatchedItems() {
        // when
        FulfilmentForm form = service().init(STORE_ID, List.of("order-1"), "default", false, false, false);

        // then
        assertThat(form.getEntries()).isEmpty();
        assertThat(form.getUnmatched()).isEmpty();
    }
}
