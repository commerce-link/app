package pl.commercelink.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import pl.commercelink.inventory.supplier.SupplierLabels;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.starter.security.CustomSecurityContext;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.orders.OrderPrintView;
import pl.commercelink.web.orders.QrCodeSvg;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
        ReflectionTestUtils.setField(ordersController, "appDomain", "https://app.example.pl");
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
    void theCardCarriesTheScanAddressOfItsOrder() {
        // given
        order();
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderCard(ORDER_ID, model);

        // then
        OrderPrintView.Card card = (OrderPrintView.Card) model.get("print");
        assertThat(card.scanUrl()).isEqualTo("https://app.example.pl/dashboard/scan/orders/" + STORE_ID + "/" + ORDER_ID);
        assertThat(card.scanQrSvg()).isEqualTo(QrCodeSvg.of(card.scanUrl()));
    }

    @Test
    void theCardOfASuperAdminCarriesTheSameScanAddress() {
        // given: a card printed by a super admin goes to the store's warehouse, where store users scan it
        order();
        when(orderItemsRepository.findByOrderId(ORDER_ID)).thenReturn(List.of());
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderCardForSuperAdmin(STORE_ID, ORDER_ID, model);

        // then
        assertThat(((OrderPrintView.Card) model.get("print")).scanUrl())
                .isEqualTo("https://app.example.pl/dashboard/scan/orders/" + STORE_ID + "/" + ORDER_ID);
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
        assertThat(collection.store()).isEqualTo("Demo");
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

    private static Order orderOf(String orderId) {
        Order order = new Order(STORE_ID);
        order.setOrderId(orderId);
        return order;
    }

    private void storeLabels() {
        when(supplierLabels.forStoreId(STORE_ID)).thenReturn(new SupplierLabels(mock(StoresRepository.class)).forStore(null));
    }

    @SuppressWarnings("unchecked")
    private static List<OrderPrintView.Card> cardsOf(ExtendedModelMap model) {
        return (List<OrderPrintView.Card>) model.get("cards");
    }

    @Test
    void theCardsOfSeveralOrdersFollowTheListEachWithItsOwnScanAddress() {
        // given
        when(ordersRepository.findByIds(STORE_ID, List.of("o-2", "o-1"))).thenReturn(List.of(orderOf("o-2"), orderOf("o-1")));
        when(orderItemsRepository.findByOrderIds(List.of("o-2", "o-1"))).thenReturn(Map.of("o-2", List.of(item("Ryzen", false))));
        storeLabels();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.getOrderCards(List.of("o-2", "o-1"), model);

        // then
        assertThat(view).isEqualTo("orders/cards");
        List<OrderPrintView.Card> cards = cardsOf(model);
        assertThat(cards).extracting(OrderPrintView.Card::orderId).containsExactly("o-2", "o-1");
        assertThat(cards).extracting(OrderPrintView.Card::scanUrl).containsExactly(
                "https://app.example.pl/dashboard/scan/orders/" + STORE_ID + "/o-2",
                "https://app.example.pl/dashboard/scan/orders/" + STORE_ID + "/o-1");
        assertThat(cards).extracting(OrderPrintView.Card::detailsHref)
                .containsExactly("/dashboard/orders/o-2", "/dashboard/orders/o-1");
        assertThat(cards.get(0).items()).extracting(OrderPrintView.ItemRow::name).containsExactly("Ryzen");
        assertThat(cards.get(1).items()).isEmpty();
    }

    @Test
    void aRepeatedIdPrintsOneCardAtItsFirstPlace() {
        // given
        when(ordersRepository.findByIds(STORE_ID, List.of("o-1", "o-2"))).thenReturn(List.of(orderOf("o-1"), orderOf("o-2")));
        when(orderItemsRepository.findByOrderIds(List.of("o-1", "o-2"))).thenReturn(Map.of());
        storeLabels();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderCards(List.of("o-1", "o-2", "o-1"), model);

        // then
        assertThat(cardsOf(model)).extracting(OrderPrintView.Card::orderId).containsExactly("o-1", "o-2");
    }

    @Test
    void ordersMissingFromTheStoreAreLeftOutAndOnlyTheSessionsStoreIsAsked() {
        // given: o-x is another store's order or no order at all, so the store's batch read does not return it
        when(ordersRepository.findByIds(STORE_ID, List.of("o-1", "o-x"))).thenReturn(List.of(orderOf("o-1")));
        when(orderItemsRepository.findByOrderIds(List.of("o-1"))).thenReturn(Map.of());
        storeLabels();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        ordersController.getOrderCards(List.of("o-1", "o-x"), model);

        // then
        assertThat(cardsOf(model)).extracting(OrderPrintView.Card::orderId).containsExactly("o-1");
        verify(ordersRepository).findByIds(STORE_ID, List.of("o-1", "o-x"));
        verifyNoMoreInteractions(ordersRepository);
    }

    @Test
    void noOrderOfTheStoreFoundAnswersNotFound() {
        // given
        when(ordersRepository.findByIds(STORE_ID, List.of("o-x"))).thenReturn(List.of());

        // when / then
        assertThatThrownBy(() -> ordersController.getOrderCards(List.of("o-x"), new ExtendedModelMap()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(orderItemsRepository);
    }

    static Stream<List<String>> badIdLists() {
        return Stream.of(
                null,
                List.of(),
                IntStream.rangeClosed(1, 51).mapToObj(i -> "o-" + i).toList(),
                List.of("o-1", "a/b"),
                List.of("o-1", "x".repeat(65)),
                List.of("o-1", ""),
                List.of("{x}"),
                List.of("..", "o-1"));
    }

    @ParameterizedTest
    @MethodSource("badIdLists")
    void aMissingTooLongOrMalformedListIsABadRequest(List<String> ids) {
        // when / then
        assertThatThrownBy(() -> ordersController.getOrderCards(ids, new ExtendedModelMap()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(ordersRepository, orderItemsRepository);
    }

    @Test
    void aWholePageOfFiftyOrdersIsAccepted() {
        // given
        List<String> fifty = IntStream.rangeClosed(1, 50).mapToObj(i -> "o-" + i).toList();
        when(ordersRepository.findByIds(STORE_ID, fifty)).thenReturn(List.of(orderOf("o-1")));
        when(orderItemsRepository.findByOrderIds(List.of("o-1"))).thenReturn(Map.of());
        storeLabels();
        ExtendedModelMap model = new ExtendedModelMap();

        // when
        String view = ordersController.getOrderCards(fifty, model);

        // then
        assertThat(view).isEqualTo("orders/cards");
        assertThat(OrdersController.MAX_CARDS).isEqualTo(50);
    }
}
