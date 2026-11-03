package pl.commercelink.marketplace;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.commercelink.baskets.Basket;
import pl.commercelink.baskets.BasketItem;
import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProduct;
import pl.commercelink.marketplace.api.PickupPoint;
import pl.commercelink.marketplace.links.MarketplaceOfferResolver;
import pl.commercelink.marketplace.links.ResolvedOffer;
import pl.commercelink.orders.*;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.starter.util.CountryCodeConverter;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MarketplaceOrderImporter {

    @Autowired
    private MarketplaceOfferResolver offerResolver;

    @Autowired
    private OrdersManager ordersManager;

    @Autowired
    private CarrierDictionary carrierDictionary;

    /** @return false when the marketplace order is already in the store and nothing was saved. */
    public boolean importOrder(Store store, String marketplaceName, MarketplaceOrder marketplaceOrder) {
        // every poll fetches orders already imported; resolving their lines would read the catalogs for nothing.
        // saveWithFulfilment checks again, against an import running at the same time.
        if (ordersManager.isAlreadyImported(store.getStoreId(), marketplaceOrder.externalOrderId())) {
            return false;
        }
        MarketplaceOfferResolver.StoreOffers offers = offerResolver.forStore(store.getStoreId());
        MarketplaceCustomer customer = marketplaceOrder.customer();
        BillingDetails billingDetails = toBillingDetails(customer);
        ShippingDetails shippingDetails = toShippingDetails(customer);

        BigDecimal commission = BigDecimal.ZERO;
        List<BasketItem> basketItems = new ArrayList<>();
        // Basket.setBasketItems normalises mfn (uppercase, spaces stripped); the marketplace key must stay raw,
        // so it is captured here, before the basket is built, and keyed by the basket item id.
        Map<String, String> rawKeysByBasketItemId = new HashMap<>();
        Map<String, ResolvedLine> resolvedByBasketItemId = new HashMap<>();

        for (MarketplaceProduct product : marketplaceOrder.products()) {
            commission = commission.add(product.commission());

            String basketItemId = UUID.randomUUID().toString();
            rawKeysByBasketItemId.put(basketItemId, product.manufacturerCode());

            String sellerOfferKey = StringUtils.trimToNull(product.sellerOfferKey());
            Optional<ResolvedOffer> resolved = offers.resolve(marketplaceName, sellerOfferKey, product.manufacturerCode());
            resolvedByBasketItemId.put(basketItemId, new ResolvedLine(sellerOfferKey, resolved.orElse(null)));

            basketItems.add(new BasketItem(
                    basketItemId,
                    product.name(),
                    resolved.map(ResolvedOffer::mfn).filter(StringUtils::isNotBlank).orElse(product.manufacturerCode()),
                    resolved.map(ResolvedOffer::category).orElse(Categories.UNCATEGORIZED),
                    product.priceGross().doubleValue(),
                    0,
                    product.quantity(),
                    null,
                    1,
                    false
            ));
        }

        basketItems.add(BasketItem.shipping(marketplaceOrder.shipping().cost().doubleValue()));

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

        Order.Builder orderBuilder = new Order.Builder(store, basket)
                .withExternalOrderId(marketplaceOrder.externalOrderId())
                .withExternalSupplierId(marketplaceOrder.externalSupplierId())
                .withPayment(payment)
                .withDeliveryCarrier(toCarrierName(store, marketplaceName, marketplaceOrder.shipping().carrier()))
                .withPreferredShippingAt(marketplaceOrder.shipping().estimatedShippingAt());

        String collectionPointCode = toCollectionPointCode(marketplaceOrder.shipping().pickupPoint());
        if (collectionPointCode != null) {
            orderBuilder.withCollectionPointCode(collectionPointCode).withShipmentType(ShipmentType.PickupPoint);
        }

        Order order = orderBuilder.build();

        List<OrderItem> orderItems = basket.getBasketItems().stream()
                .map(i -> {
                    OrderItem orderItem = OrderItem.fromBasketItem(order.getOrderId(), i);
                    if (!i.isService()) {
                        orderItem.setExternalItemId(rawKeysByBasketItemId.get(i.getId()));
                        ResolvedLine line = resolvedByBasketItemId.get(i.getId());
                        orderItem.setMarketplaceOfferKey(line.sellerOfferKey());
                        // the MFN step reproduces the pre-link behaviour, which never filled the ean
                        if (line.offer() != null && line.offer().source() != ResolvedOffer.Source.MFN) {
                            orderItem.setEan(line.offer().ean());
                        }
                        // a linked line stands for a catalog product, so it records that product's manufacturer code
                        // next to the sku; the own-offer step deliberately keeps its pre-link fields, so its items
                        // do not change
                        if (line.offer() != null && line.offer().source() == ResolvedOffer.Source.LINK) {
                            orderItem.setManufacturerCode(line.offer().mfn());
                        }
                    }
                    return orderItem;
                })
                .collect(Collectors.toList());

        return ordersManager.saveWithFulfilment(order, orderItems);
    }

    String toCarrierName(Store store, String marketplaceName, String shippingCarrier) {
        return carrierDictionary
                .translate(marketplaceName, store.getConfigurationValue(IntegrationType.SHIPPING_PROVIDER), shippingCarrier)
                .orElse(shippingCarrier);
    }

    static String toCollectionPointCode(PickupPoint pickupPoint) {
        return pickupPoint != null ? StringUtils.trimToNull(pickupPoint.code()) : null;
    }

    BillingDetails toBillingDetails(MarketplaceCustomer customer) {
        BillingDetails billing = new BillingDetails();
        MarketplaceCustomer.Address address = customer.billingAddress();

        if (customer.customerType() == MarketplaceCustomer.CustomerType.COMPANY) {
            billing.setCompanyName(address.name());
            billing.setTaxId(customer.taxId());
        } else {
            String fullName = StringUtils.defaultString(StringUtils.normalizeSpace(address.name()));
            billing.setName(StringUtils.substringBeforeLast(fullName, " "));
            billing.setSurname(StringUtils.substringAfterLast(fullName, " "));
        }

        billing.setEmail(customer.email());
        billing.setPhone(address.phone());
        billing.setStreetAndNumber(address.street());
        billing.setPostalCode(address.postalCode());
        billing.setCity(address.city());
        billing.setCountry(CountryCodeConverter.getCountryCode(address.country()));

        return billing;
    }

    ShippingDetails toShippingDetails(MarketplaceCustomer customer) {
        ShippingDetails shipping = new ShippingDetails();
        MarketplaceCustomer.Address address = customer.shippingAddress();

        String fullName = StringUtils.defaultString(StringUtils.normalizeSpace(address.name()));
        shipping.setName(StringUtils.substringBeforeLast(fullName, " "));
        shipping.setSurname(StringUtils.substringAfterLast(fullName, " "));
        shipping.setCompanyName(customer.company());
        shipping.setEmail(customer.email());
        shipping.setPhone(address.phone());
        shipping.setStreetAndNumber(address.street());
        shipping.setPostalCode(address.postalCode());
        shipping.setCity(address.city());
        shipping.setCountry(CountryCodeConverter.getCountryCode(address.country()));

        return shipping;
    }

    private record ResolvedLine(String sellerOfferKey, ResolvedOffer offer) {
    }

    private PaymentSource resolvePaymentSource(String paymentType) {
        try {
            return PaymentSource.valueOf(paymentType);
        } catch (IllegalArgumentException e) {
            return PaymentSource.BankTransfer;
        }
    }
}
