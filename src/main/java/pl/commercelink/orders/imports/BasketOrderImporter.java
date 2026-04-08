package pl.commercelink.orders.imports;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.baskets.BasketsRepository;
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
public class BasketOrderImporter implements OrderImporter {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private BasketsRepository basketsRepository;

    @Autowired
    private OrdersManager ordersManager;

    @Override
    public boolean supports(String storeId, ClientDataDto dto) {
        return dto.getOrderReferenceType() == OrderReferenceType.Basket;
    }

    @Override
    public Order _import(String storeId, ClientDataDto dto) {
        Store store = storesRepository.findById(storeId);
        Basket basket = basketsRepository.findById(storeId, dto.getOrderReference())
                .orElseThrow(() -> new IllegalArgumentException("Basket not found"));

        Order order = new Order.Builder(store, basket)
                .withBillingDetails(dto.getBillingDetails())
                .withShippingDetails(dto.getShippingDetails())
                .withShipmentType(dto.getShipmentType())
                .withPaymentSource(dto.getPaymentSource())
                .build();

        List<OrderItem> orderItems = basket.getBasketItems().stream()
                .map(i -> OrderItem.fromBasketItem(order.getOrderId(), i))
                .collect(Collectors.toList());

        basket.resolveDeliveryOption(store).ifPresent(opt -> {
                orderItems.add(OrderItem.fromDeliveryOption(order.getOrderId(), opt));
                order.increaseTotalPrice(opt.getPrice());
        });

        ordersManager.saveWithFulfilment(order, orderItems);

        cleanUp(getStoreId(), dto);

        return order;
    }

    private void cleanUp(String storeId, ClientDataDto dto) {
        Basket basket = new Basket();
        basket.setStoreId(storeId);
        basket.setBasketId(dto.getOrderReference());

        basketsRepository.delete(basket);
    }

}
