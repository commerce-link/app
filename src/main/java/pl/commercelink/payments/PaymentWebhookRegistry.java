package pl.commercelink.payments;

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
import pl.commercelink.stores.StoresRepository;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class PaymentWebhookRegistry {

    private final BasketsRepository basketsRepository;
    private final OrdersManager ordersManager;
    private final RouterFunction<ServerResponse> routes;

    PaymentWebhookRegistry(PaymentProviderFactory paymentProviderFactory,
                           StoresRepository storesRepository,
                           BasketsRepository basketsRepository,
                           OrdersManager ordersManager) {
        this.basketsRepository = basketsRepository;
        this.ordersManager = ordersManager;

        this.routes = EventBindingRegistrar.forDescriptors(paymentProviderFactory.availableProviders())
                .<PaymentWebhookResult>withWebhooks(
                        "/Store/{storeId}/Webhooks/Payments/",
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

        List<OrderItem> orderItems = basket.getBasketItems().stream()
                .map(i -> OrderItem.fromBasketItem(order.getOrderId(), i))
                .collect(Collectors.toList());

        basket.resolveDeliveryOption(store).ifPresent(opt -> {
            OrderItem deliveryItem = OrderItem.fromDeliveryOption(order.getOrderId(), opt);
            deliveryItem.setPosition(orderItems.size());
            orderItems.add(deliveryItem);
            order.increaseTotalPrice(opt.getPrice());
        });

        ordersManager.saveWithFulfilment(order, orderItems);

        basketsRepository.delete(basket);
    }
}
