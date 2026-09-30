package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.ui.ExtendedModelMap;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderPrintView;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** The two printouts: the redesigned templates, and a way back that works for the store and for a super admin. */
@ExtendWith(MockitoExtension.class)
class OrdersControllerPrintTest {

    private static final String STORE_ID = "store-1";
    private static final String ORDER_ID = "order-1";

    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;
    @Mock
    private StoresRepository storesRepository;
    @Mock
    private SupplierLabels supplierLabels;
    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private OrdersController ordersController;

    private MockedStatic<CustomSecurityContext> securityStub;

    @BeforeEach
    void setUp() {
        securityStub = mockStatic(CustomSecurityContext.class);
        securityStub.when(CustomSecurityContext::getStoreId).thenReturn(STORE_ID);
    }

    @AfterEach
    void tearDown() {
        securityStub.close();
    }

    private Order order() {
        Order order = new Order(STORE_ID);
        order.setOrderId(ORDER_ID);
        when(ordersRepository.findById(STORE_ID, ORDER_ID)).thenReturn(order);
        return order;
    }

    private Store store() {
        Store store = new Store();
        store.setStoreId(STORE_ID);
        store.setName("Demo");
        when(storesRepository.findById(STORE_ID)).thenReturn(store);
        return store;
    }

    private static OrderItem item(String name, boolean service) {
        OrderItem item = new OrderItem(ORDER_ID, "CPU", name, 1, 100, "SKU", false, 0);
        item.setService(service);
        return item;
    }

    @Test
    void theCardOfTheStoreRendersTheNewTemplateAndLeadsBackToTheOrder() {
        // given
        order();
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item("Ryzen", false)));
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.getOrderCard(ORDER_ID, model);

        // then
        assertThat(view).isEqualTo("orders/card");
        OrderPrintView.Card card = (OrderPrintView.Card) model.get("print");
        assertThat(card.detailsHref()).isEqualTo("/dashboard/orders/" + ORDER_ID);
        assertThat(card.items()).extracting(OrderPrintView.ItemRow::name).containsExactly("Ryzen");
    }

    @Test
    void theCardOfASuperAdminLeadsBackToTheStoreScopedOrder() {
        // given
        order();
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.getOrderCardForSuperAdmin(STORE_ID, ORDER_ID, model);

        // then
        assertThat(view).isEqualTo("orders/card");
        assertThat(((OrderPrintView.Card) model.get("print")).detailsHref())
                .isEqualTo("/dashboard/store/" + STORE_ID + "/orders/" + ORDER_ID);
    }

    @Test
    void theProtocolListsProductsOnlyAndLeadsASuperAdminBackToTheStoreScopedOrder() {
        // given
        order();
        Store store = store();
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(item("Ryzen", false), item("Montaż", true)));
        when(supplierLabels.forStore(store)).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(store));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.getOrderCollectionProtocolForSuperAdmin(STORE_ID, ORDER_ID, model);

        // then
        assertThat(view).isEqualTo("orders/collection");
        OrderPrintView.Collection collection = (OrderPrintView.Collection) model.get("print");
        assertThat(collection.detailsHref()).isEqualTo("/dashboard/store/" + STORE_ID + "/orders/" + ORDER_ID);
        assertThat(collection.store()).isEqualTo("store-1 (Demo)");
        assertThat(collection.location()).isEqualTo("Kraków, PL");
        assertThat(collection.items()).extracting(OrderPrintView.ItemRow::name).containsExactly("Ryzen");
    }

    @Test
    void theCardNamesTheStoresWarehouseInTheRequestsLanguage() {
        // given
        order();
        OrderItem fromWarehouse = item("Pamięć", false);
        fromWarehouse.setDeliveryId(OrderItem.GENERIC_WAREHOUSE_ORDER_NO);
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of(fromWarehouse));
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        when(messageSource.getMessage(eq("order.item.delivery.warehouse"), any(), any())).thenReturn("Magazyn sklepu");
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderCard(ORDER_ID, model);

        // then
        assertThat(((OrderPrintView.Card) model.get("print")).items()).extracting(OrderPrintView.ItemRow::delivery)
                .containsExactly("Magazyn sklepu");
    }
}
