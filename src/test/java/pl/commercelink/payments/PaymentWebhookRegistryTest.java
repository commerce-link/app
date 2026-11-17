package pl.commercelink.payments;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.discounts.DiscountService;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.payments.api.PaymentWebhookResult;
import pl.commercelink.stores.CheckoutConfiguration;
import pl.commercelink.stores.DeliveryOption;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentWebhookRegistryTest {

    private static final String STORE_ID = "abc123def4";
    private static final String BASKET_ID = "basket-1";

    @Mock private PaymentProviderFactory paymentProviderFactory;
    @Mock private StoresRepository storesRepository;
    @Mock private BasketsRepository basketsRepository;
    @Mock private OrdersManager ordersManager;
    @Mock private StoreActivity storeActivity;
    @Mock private DiscountService discountService;
    @Captor private ArgumentCaptor<Order> order;
    @Captor private ArgumentCaptor<List<OrderItem>> orderItems;

    private final Store store = new Store();
    private final Basket basket = new Basket();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private PaymentWebhookRegistry registry;

    @BeforeEach
    void setUp() {
        store.setStoreId(STORE_ID);
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        basket.setStoreId(STORE_ID);
        basket.setBasketId(BASKET_ID);
        basket.setBillingDetails(new BillingDetails());
        basket.setShippingDetails(new ShippingDetails());
        when(paymentProviderFactory.availableProviders()).thenReturn(List.of());
        when(basketsRepository.findById(STORE_ID, BASKET_ID)).thenReturn(Optional.of(basket));
        registry = new PaymentWebhookRegistry(paymentProviderFactory, storesRepository, basketsRepository,
                ordersManager, storeActivity, discountService);
        logs.start();
        registryLogger().addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        registryLogger().detachAppender(logs);
    }

    @Test
    void paymentOfAnInactiveStoreStillBecomesAnOrderAndRaisesAnError() {
        // given
        when(storeActivity.isActive(store)).thenReturn(false);

        // when
        createOrder();

        // then
        verify(ordersManager).saveWithFulfilment(argThat(order -> BASKET_ID.equals(order.getOrderId())), any());
        verify(basketsRepository).delete(basket);
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains(BASKET_ID, STORE_ID);
        });
    }

    @Test
    void paymentOfAnActiveStoreBecomesAnOrderWithoutAnError() {
        // given
        when(storeActivity.isActive(store)).thenReturn(true);

        // when
        createOrder();

        // then
        verify(ordersManager).saveWithFulfilment(argThat(order -> BASKET_ID.equals(order.getOrderId())), any());
        assertThat(logs.list).isEmpty();
    }

    @Test
    void redeemsCodeAfterSavingOrder() {
        // given
        basket.setDiscountCode("LATO-10");
        when(storeActivity.isActive(store)).thenReturn(true);

        // when
        createOrder();

        // then
        InOrder inOrder = inOrder(ordersManager, discountService, basketsRepository);
        inOrder.verify(ordersManager).saveWithFulfilment(any(), any());
        inOrder.verify(discountService).redeem(STORE_ID, "LATO-10");
        inOrder.verify(basketsRepository).delete(basket);
    }

    @Test
    void noRedeemWithoutCode() {
        // given
        when(storeActivity.isActive(store)).thenReturn(true);

        // when
        createOrder();

        // then
        verifyNoInteractions(discountService);
    }

    @Test
    void orderOfADiscountedBasketCostsWhatTheCustomerWasCharged() {
        // given: the basket of POST /Checkout with 99.99 zł of promotions, charged 2615.01 zł with the courier
        DeliveryOption courier = new DeliveryOption();
        courier.setName("Kurier");
        courier.setPrice(15.00);
        store.setCheckoutConfiguration(new CheckoutConfiguration());
        store.getCheckoutConfiguration().addDeliveryOption(courier);
        BasketItem assembly = item("Montaż", 100.00, 1);
        assembly.setService(true);
        basket.setBasketItems(List.of(item("Karta graficzna", 961.54, 2), item("Pamięć RAM", 192.31, 3), assembly));
        basket.setDeliveryOptionId(courier.getId());
        basket.setDiscountAmount(99.99);
        when(storeActivity.isActive(store)).thenReturn(true);

        // when
        createOrder();

        // then
        verify(ordersManager).saveWithFulfilment(order.capture(), orderItems.capture());
        assertThat(order.getValue().getTotalPrice()).isEqualTo(2615.01);
        assertThat(order.getValue().getDiscountAmount()).isEqualTo(99.99);
        assertThat(order.getValue().hasPromotions()).isTrue();
        assertThat(orderItems.getValue()).extracting(OrderItem::getPrice, OrderItem::getQty)
                .containsExactly(tuple(961.54, 2), tuple(192.31, 3), tuple(100.00, 1), tuple(15.00, 1));
    }

    private static BasketItem item(String name, double unitPrice, long qty) {
        return new BasketItem("pim-" + name, name, "590123412345" + qty, "Podzespoły", unitPrice, 0, qty, "catalog-1", 2, false);
    }

    private void createOrder() {
        ReflectionTestUtils.invokeMethod(registry, "createOrder", store, "PayU",
                new PaymentWebhookResult(BASKET_ID, "ref-1", 0.0, true));
    }

    private static Logger registryLogger() {
        return (Logger) LoggerFactory.getLogger(PaymentWebhookRegistry.class);
    }
}
