package pl.commercelink.orders.pos;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.starter.util.OperationResult;
import pl.commercelink.stores.Store;
import pl.commercelink.stores.StoresRepository;

import java.util.Locale;
import java.util.Optional;

@Component
public class PosOrderCreator {

    @Autowired
    private StoresRepository storesRepository;

    @Autowired
    private OrdersRepository ordersRepository;

    @Autowired
    private MessageSource messageSource;

    public OperationResult<Order> create(String storeId, Locale locale) {
        Store store = storesRepository.findById(storeId);

        BillingDetails storeBillingDetails = store.getBillingDetails();
        if (storeBillingDetails == null || !storeBillingDetails.isProperlyFilled()) {
            return OperationResult.failure("error.pos.missing.store.billing.details");
        }

        Optional<ShippingDetails> pickUpAddressOpt = store.getDefaultPickupAddress();
        if (pickUpAddressOpt.isEmpty() || !pickUpAddressOpt.get().isProperlyFilled()) {
            return OperationResult.failure("error.pos.missing.pickup.address");
        }

        ShippingDetails pickUpAddress = pickUpAddressOpt.get();
        String customerName = messageSource.getMessage("order.pos.customer.name", null, locale);
        String pickUpRecipientName = messageSource.getMessage("order.pos.shipping.recipient", null, locale);

        BillingDetails billingDetails = BillingDetails.walkInCustomer(storeBillingDetails, customerName);
        ShippingDetails shippingDetails = ShippingDetails.pickUpPoint(pickUpAddress, pickUpRecipientName);

        Order order = Order.Builder.forPos(store, billingDetails, shippingDetails).build();
        ordersRepository.save(order);

        return OperationResult.success(order);
    }
}
