package pl.commercelink.payments;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.Payment;
import pl.commercelink.orders.PaymentSource;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.payments.api.PaymentWebhookRequest;
import pl.commercelink.payments.api.PaymentWebhookResult;
import pl.commercelink.provider.EventBindingRegistrar;
import pl.commercelink.provider.api.EventBinding;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class PaymentWebhookRegistry {

    private final PaymentProviderFactory paymentProviderFactory;
    private final StoresRepository storesRepository;
    private final BasketsRepository basketsRepository;
    private final OrdersManager ordersManager;
    private final RouterFunction<ServerResponse> routes;

    PaymentWebhookRegistry(PaymentProviderFactory paymentProviderFactory,
                           StoresRepository storesRepository,
                           BasketsRepository basketsRepository,
                           OrdersManager ordersManager,
                           SqsAsyncClient sqsAsyncClient) {
        this.paymentProviderFactory = paymentProviderFactory;
        this.storesRepository = storesRepository;
        this.basketsRepository = basketsRepository;
        this.ordersManager = ordersManager;

        EventBindingRegistrar registrar = new EventBindingRegistrar(sqsAsyncClient);
        RouterFunctions.Builder builder = RouterFunctions.route();

        for (PaymentProviderDescriptor descriptor : paymentProviderFactory.availableProviders()) {
            for (EventBinding<?> binding : descriptor.bindings()) {
                registrar.register(
                        binding,
                        null,
                        builder,
                        "/Store/{storeId}/Webhooks/Payments/",
                        null,
                        (event, storeId, headers) ->
                                processPayment((String) event, headers, storeId, descriptor));
            }
        }
        this.routes = EventBindingRegistrar.buildOrEmpty(builder);
    }

    @Bean
    RouterFunction<ServerResponse> paymentWebhookRoutes() {
        return routes;
    }

    private void processPayment(String payload, Map<String, String> headers,
                                String storeId, PaymentProviderDescriptor descriptor) {
        Store store = storesRepository.findById(storeId);
        Map<String, String> configuration = paymentProviderFactory.loadConfiguration(store, descriptor.name());

        PaymentProvider provider = descriptor.create(configuration);
        PaymentWebhookResult result = provider.processWebhook(new PaymentWebhookRequest(payload, headers));

        if (result.processable()) {
            createOrder(store, descriptor.displayName(), result);
        }
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
            orderItems.add(OrderItem.fromDeliveryOption(order.getOrderId(), opt));
            order.increaseTotalPrice(opt.getPrice());
        });

        ordersManager.saveWithFulfilment(order, orderItems);

        basketsRepository.delete(basket);
    }
}
