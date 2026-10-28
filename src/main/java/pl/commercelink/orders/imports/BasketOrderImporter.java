package pl.commercelink.orders.imports;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketsRepository;
import pl.commercelink.discounts.DiscountService;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;
import pl.commercelink.web.dtos.ClientDataDto;

import java.util.List;
import java.util.stream.Collectors;

import static pl.commercelink.starter.security.CustomSecurityContext.getStoreId;

@Component
@RequiredArgsConstructor
public class BasketOrderImporter {

    private final StoresRepository storesRepository;
    private final BasketsRepository basketsRepository;
    private final OrdersManager ordersManager;
    private final DiscountService discountService;

    public Order _import(String storeId, ClientDataDto dto) {
        Store store = storesRepository.findById(storeId);
        Basket basket = basketsRepository.findById(storeId, dto.getOrderReference())
                .orElseThrow(() -> new IllegalArgumentException("Basket not found"));

        basket.setBillingDetails(dto.getBillingDetails());
        basket.setShippingDetails(dto.getShippingDetails());

        Order order = new Order.Builder(store, basket)
                .withShipmentType(dto.getShipmentType())
                .withPaymentSource(dto.getPaymentSource())
                .build();

        List<OrderItem> orderItems = basket.getEffectiveBasketItems().stream()
                .map(i -> OrderItem.fromBasketItem(order.getOrderId(), i))
                .collect(Collectors.toList());

        basket.resolveDeliveryOption(store).ifPresent(opt -> {
                OrderItem deliveryItem = OrderItem.fromDeliveryOption(order.getOrderId(), opt);
                orderItems.add(deliveryItem);
                order.increaseTotalPrice(opt.getPrice());
        });

        ordersManager.saveWithFulfilment(order, orderItems);

        if (basket.hasDiscountCode()) {
            discountService.redeem(storeId, basket.getDiscountCode());
        }

        cleanUp(getStoreId(), dto);

        return order;
    }

    private void cleanUp(String storeId, ClientDataDto dto) {
        basketsRepository.findById(storeId, dto.getOrderReference())
                .ifPresent(basketsRepository::delete);
    }

}
