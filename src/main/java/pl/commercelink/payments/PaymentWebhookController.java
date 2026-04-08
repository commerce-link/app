package pl.commercelink.payments;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.orders.*;
import pl.commercelink.payments.api.PaymentProvider;
import pl.commercelink.payments.api.PaymentProviderDescriptor;
import pl.commercelink.payments.api.PaymentWebhookRequest;
import pl.commercelink.payments.api.PaymentWebhookResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.StatusDto;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/Store/{storeId}/Webhooks/Payments")
public class PaymentWebhookController {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private OrdersManager ordersManager;

    @Autowired
    private PaymentProviderFactory paymentProviderFactory;

    @PostMapping("/{providerName}")
    @ResponseBody
    public StatusDto receiveWebhook(
            @PathVariable("storeId") String storeId,
            @PathVariable("providerName") String providerName,
            @RequestBody String payload,
            @RequestHeader Map<String, String> headers
    ) {
        String normalizedName = providerName.toLowerCase();
        PaymentProviderDescriptor descriptor = paymentProviderFactory.getDescriptor(normalizedName);
        if (descriptor == null) {
            throw new RuntimeException("Unknown payment provider: " + providerName);
        }

        Store store = storesRepository.findById(storeId);
        Map<String, String> configuration = paymentProviderFactory.loadConfiguration(store, normalizedName);

        PaymentProvider provider = descriptor.create(configuration);
        PaymentWebhookResult result = provider.processWebhook(new PaymentWebhookRequest(payload, headers));

        if (result.processable()) {
            createOrder(store, descriptor.displayName(), result);
        }

        return new StatusDto("OK");
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
