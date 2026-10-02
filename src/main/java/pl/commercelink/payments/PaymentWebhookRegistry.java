package pl.commercelink.payments;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.payments.api.PaymentWebhookResult;
import pl.commercelink.provider.EventBindingRegistrar;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoreActivity;
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class PaymentWebhookRegistry {

    /** Followed by the path of the provider's webhook binding; shown to the store admin on the payment gateway page. */
    public static final String WEBHOOK_PATH_PREFIX = "/Store/{storeId}/Webhooks/Payments/";

    private final BasketsRepository basketsRepository;
    private final OrdersManager ordersManager;
    private final StoreActivity storeActivity;
    private final RouterFunction<ServerResponse> routes;

    PaymentWebhookRegistry(PaymentProviderFactory paymentProviderFactory,
                           StoresRepository storesRepository,
                           BasketsRepository basketsRepository,
                           OrdersManager ordersManager,
                           StoreActivity storeActivity) {
        this.basketsRepository = basketsRepository;
        this.ordersManager = ordersManager;
        this.storeActivity = storeActivity;

        this.routes = EventBindingRegistrar.forDescriptors(paymentProviderFactory.availableProviders())
                .<PaymentWebhookResult>withWebhooks(
                        WEBHOOK_PATH_PREFIX,
                        (descriptor, storeId) -> paymentProviderFactory.loadConfiguration(
                                storesRepository.findById(storeId), descriptor.name()),
                        (descriptor, storeId, result) -> {
                            if (result.processable()) {
                                Store store = storesRepository.findById(storeId);
                                createOrder(store, descriptor.displayName(), result);
                            }
                        })
                .register();
    }

    @Bean
    RouterFunction<ServerResponse> paymentWebhookRoutes() {
        return routes;
    }

    private void createOrder(Store store, String providerName, PaymentWebhookResult result) {
        Basket basket = basketsRepository.findById(store.getStoreId(), result.orderId())
                .orElseThrow(() -> new RuntimeException("Basket not found: " + result.orderId()));

        Order.Builder orderBuilder = new Order.Builder(store, basket)
                .withOrderId(basket.getBasketId())
                .withPayment(new Payment(result.reference(), providerName, PaymentSource.OnlinePayment, 0, result.fee()));

        basket.resolveDeliveryOption(store).ifPresent(opt ->
                orderBuilder.withShipmentType(opt.getType())
        );

        Order order = orderBuilder.build();

        List<OrderItem> orderItems = basket.getEffectiveBasketItems().stream()
                .map(i -> OrderItem.fromBasketItem(order.getOrderId(), i))
                .collect(Collectors.toList());

        basket.resolveDeliveryOption(store).ifPresent(opt -> {
            OrderItem deliveryItem = OrderItem.fromDeliveryOption(order.getOrderId(), opt);
            orderItems.add(deliveryItem);
            order.increaseTotalPrice(opt.getPrice());
        });

        ordersManager.saveWithFulfilment(order, orderItems);

        basketsRepository.delete(basket);

        // the customer has paid, but the listeners skip the order of an inactive store and its owner cannot act on it
        // in the read-only dashboard, so someone has to step in
        if (!storeActivity.isActive(store)) {
            log.error("Order {} was paid in inactive store {}: it is neither fulfilled nor confirmed to the customer",
                    order.getOrderId(), store.getStoreId());
        }
    }
}
