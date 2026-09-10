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
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderLifecycle;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.stores.ConnectionMode;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreSupplierConnection;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.stores.SupplierScope;
import pl.commercelink.warehouse.WarehouseFulfilmentService;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualOrderFulfilmentRoutingTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

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
    @Mock
    private OrderItem orderItem;

    @Test
    void initCallsInventoryWithFulfilmentScope() {
        // given
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(orderItemsRepository.findByOrderIdAndStatus(ORDER_ID, FulfilmentStatus.New)).thenReturn(List.of(orderItem));

        ManualOrderFulfilment service = new ManualOrderFulfilment(
                inventory, ordersRepository, orderLifecycle, orderItemsRepository,
                warehouseFulfilmentService, supplierRegistry, storesRepository);

        // when
        service.init(STORE_ID, List.of(ORDER_ID), "default", false, false, false);

        // then
        verify(inventory).withEnabledSuppliersAndWarehouseData(eq(STORE_ID), eq(SupplierScope.FULFILMENT));
    }

    @Test
    void initReadsTheStoreAndTheOrdersToHonourTheirSupplierBinding() {
        // given
        when(inventory.withEnabledSuppliersAndWarehouseData(STORE_ID, SupplierScope.FULFILMENT)).thenReturn(inventoryView);
        when(orderItemsRepository.findByOrderIdAndStatus(ORDER_ID, FulfilmentStatus.New)).thenReturn(List.of(orderItem));

        ManualOrderFulfilment service = new ManualOrderFulfilment(
                inventory, ordersRepository, orderLifecycle, orderItemsRepository,
                warehouseFulfilmentService, supplierRegistry, storesRepository);

        // when
        service.init(STORE_ID, List.of(ORDER_ID), "default", false, false, false);

        // then
        verify(storesRepository).findById(STORE_ID);
        verify(ordersRepository).findById(STORE_ID, ORDER_ID);
    }

    private static Store storeWith(StoreSupplierConnection... connections) {
        FulfilmentConfiguration config = new FulfilmentConfiguration();
        config.setSupplierConnections(new ArrayList<>(List.of(connections)));
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(config);
        return store;
    }

    private static StoreSupplierConnection connection(String supplierName, String externalSupplierId) {
        StoreSupplierConnection connection = new StoreSupplierConnection(supplierName, ConnectionMode.GLOBAL);
        connection.setExternalSupplierId(externalSupplierId);
        return connection;
    }

    private static Order routedOrder(String externalSupplierId) {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        order.setExternalSupplierId(externalSupplierId);
        return order;
    }

    private static FulfilmentForm acceptedFormFor(String provider) {
        FulfilmentSource source = new FulfilmentSource();
        source.setProvider(provider);
        FulfilmentAllocation allocation = new FulfilmentAllocation();
        allocation.setOrderId(ORDER_ID);
        allocation.setOrderItemId("item-1");
        FulfilmentGroup group = new FulfilmentGroup(source, List.of(allocation), true);
        return new FulfilmentForm("orders", "redirect:/dashboard/orders", List.of(ORDER_ID), List.of(group));
    }

    private ManualOrderFulfilment service() {
        return new ManualOrderFulfilment(
                inventory, ordersRepository, orderLifecycle, orderItemsRepository,
                warehouseFulfilmentService, supplierRegistry, storesRepository);
    }

    @Test
    void commitDropsAPostedCandidateTheMarketplaceDidNotChoose() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(storeWith(connection("Acme", "2")));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder("2"));

        // when
        service().commit(STORE_ID, acceptedFormFor("Bravo"));

        // then
        verify(orderItemsRepository, never()).findByOrderId(ORDER_ID);
    }

    @Test
    void commitKeepsAPostedCandidateOfTheSupplierTheMarketplaceChose() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(storeWith(connection("Acme", "2")));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder("2"));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        // when
        service().commit(STORE_ID, acceptedFormFor("Acme"));

        // then
        verify(orderItemsRepository).findByOrderId(ORDER_ID);
    }

    @Test
    void commitLeavesAnUnroutedOrderAlone() {
        // given
        when(storesRepository.findById(STORE_ID)).thenReturn(storeWith(connection("Acme", "2")));
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(routedOrder(null));
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());

        // when
        service().commit(STORE_ID, acceptedFormFor("Bravo"));

        // then
        verify(orderItemsRepository).findByOrderId(ORDER_ID);
    }
}
