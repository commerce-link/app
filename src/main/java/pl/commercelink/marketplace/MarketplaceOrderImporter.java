package pl.commercelink.marketplace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProduct;
import pl.commercelink.orders.*;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.starter.util.CountryCodeConverter;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketplaceOrderImporter {

    @Autowired
    private PimCatalog pimCatalog;

    @Autowired
    private OrdersManager ordersManager;

    public void importOrder(Store store, String marketplaceName, MarketplaceOrder marketplaceOrder) {
        MarketplaceCustomer customer = marketplaceOrder.customer();
        BillingDetails billingDetails = toBillingDetails(customer);
        ShippingDetails shippingDetails = toShippingDetails(customer);

        BigDecimal commission = BigDecimal.ZERO;
        List<BasketItem> basketItems = new ArrayList<>();

        for (MarketplaceProduct product : marketplaceOrder.products()) {
            commission = commission.add(product.commission());

            basketItems.add(new BasketItem(
                    UUID.randomUUID().toString(),
                    product.name(),
                    product.manufacturerCode(),
                    resolveProductCategory(product.manufacturerCode()),
                    product.priceGross().doubleValue(),
                    0,
                    product.quantity(),
                    null,
                    1,
                    false
            ));
        }

        basketItems.add(BasketItem.shipping(marketplaceOrder.shippingCost().doubleValue()));

        Basket basket = Basket.builder(store)
                .withSource(new OrderSource(marketplaceName, OrderSourceType.Marketplace))
                .withBillingDetails(billingDetails)
                .withShippingDetails(shippingDetails)
                .withBasketItems(basketItems)
                .build();

        Payment payment = new Payment(
                marketplaceOrder.paymentTransactionId(),
                "",
                resolvePaymentSource(marketplaceOrder.paymentType()),
                0,
                commission.doubleValue()
        );

        Order order = new Order.Builder(store, basket)
                .withExternalOrderId(marketplaceOrder.externalOrderId())
                .withPayment(payment)
                .build();

        List<OrderItem> orderItems = basket.getBasketItems().stream()
                .map(i -> OrderItem.fromBasketItem(order.getOrderId(), i))
                .collect(Collectors.toList());

        ordersManager.saveWithFulfilment(order, orderItems);
    }

    private BillingDetails toBillingDetails(MarketplaceCustomer customer) {
        BillingDetails billing = new BillingDetails();
        MarketplaceCustomer.Address address = customer.billingAddress();

        if (customer.customerType() == MarketplaceCustomer.CustomerType.COMPANY) {
            billing.setCompanyName(address.name());
            billing.setTaxId(customer.taxId());
        } else {
            String[] parts = address.name().split(" ");
            billing.setName(parts[0]);
            billing.setSurname(parts.length > 1 ? parts[1] : "");
        }

        billing.setEmail(customer.email());
        billing.setPhone(address.phone());
        billing.setStreetAndNumber(address.street());
        billing.setPostalCode(address.postalCode());
        billing.setCity(address.city());
        billing.setCountry(CountryCodeConverter.getCountryCode(address.country()));

        return billing;
    }

    private ShippingDetails toShippingDetails(MarketplaceCustomer customer) {
        ShippingDetails shipping = new ShippingDetails();
        MarketplaceCustomer.Address address = customer.shippingAddress();

        String[] parts = address.name().split(" ");
        shipping.setName(parts[0]);
        shipping.setSurname(parts.length > 1 ? parts[1] : "");
        shipping.setCompanyName(customer.company());
        shipping.setEmail(customer.email());
        shipping.setPhone(address.phone());
        shipping.setStreetAndNumber(address.street());
        shipping.setPostalCode(address.postalCode());
        shipping.setCity(address.city());
        shipping.setCountry(CountryCodeConverter.getCountryCode(address.country()));

        return shipping;
    }

    private ProductCategory resolveProductCategory(String mfn) {
        return pimCatalog.findByMpn(mfn)
                .map(PimEntry::category)
                .orElse(ProductCategory.Other);
    }

    private PaymentSource resolvePaymentSource(String paymentType) {
        try {
            return PaymentSource.valueOf(paymentType);
        } catch (IllegalArgumentException e) {
            return PaymentSource.DirectDebit;
        }
    }
}
