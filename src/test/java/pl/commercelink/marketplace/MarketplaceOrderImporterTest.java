package pl.commercelink.marketplace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.marketplace.api.MarketplaceCustomer;
import pl.commercelink.marketplace.api.MarketplaceOrder;
import pl.commercelink.marketplace.api.MarketplaceProduct;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Integration;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrderImporterTest {

    @Spy
    private CarrierDictionary carrierDictionary = dictionaryForMorele();

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private OrdersManager ordersManager;

    @InjectMocks
    private MarketplaceOrderImporter importer;

    private static CarrierDictionary dictionaryForMorele() {
        CarrierDictionary dictionary = new CarrierDictionary();
        dictionary.setCarriers(Map.of("Morele", Map.of("furgonetka", "{\"2\":\"InPost\",\"6\":\"Zabka\"}")));
        return dictionary;
    }

    private static Store storeShippingWith(String shippingProvider) {
        Store store = new Store();
        store.setIntegrations(List.of(new Integration(IntegrationType.SHIPPING_PROVIDER, shippingProvider)));
        return store;
    }

    @Test
    void keepsTheDeliveryStreetIntactForPointOrders() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("Prosta 1", shipping.getStreetAndNumber());
    }

    @Test
    void rendersRawStreetWhenNoPickupPoint() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("Prosta 1", shipping.getStreetAndNumber());
    }

    @Test
    void toleratesNullShippingName() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                null, "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("", shipping.getName());
        assertEquals("", shipping.getSurname());
    }

    @Test
    void storesTheShippingProviderCarrierNameInsteadOfTheMarketplaceCode() {
        // when / then
        assertEquals("InPost", importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", "2"));
        assertEquals("Zabka", importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", "6"));
    }

    @Test
    void keepsTheMarketplaceValueWhenNothingMapsIt() {
        // when / then
        assertEquals("99", importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", "99"));
        assertEquals("2", importer.toCarrierName(storeShippingWith("apaczka"), "Morele", "2"));
        assertNull(importer.toCarrierName(storeShippingWith("furgonetka"), "Morele", null));
    }

    @Test
    void toleratesAStoreWithoutAShippingProvider() {
        // when / then
        assertEquals("2", importer.toCarrierName(new Store(), "Morele", "2"));
    }

    @Test
    void toleratesNullBillingName() {
        // given
        MarketplaceCustomer.Address billingAddress = new MarketplaceCustomer.Address(
                null, "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, billingAddress, billingAddress);

        // when
        BillingDetails billing = importer.toBillingDetails(customer);

        // then
        assertEquals("", billing.getName());
        assertEquals("", billing.getSurname());
    }

    @Test
    void setsTheExternalItemIdOnProductItemsButNotOnTheShippingItem() {
        // given
        when(pimCatalog.findByMpn(anyString())).thenReturn(Optional.empty());
        Store store = new Store();
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        MarketplaceOrder marketplaceOrder = orderWithProduct("SKU-1");

        // when
        importer.importOrder(store, "Allegro", marketplaceOrder);

        // then
        List<OrderItem> orderItems = capturedOrderItems();
        OrderItem productItem = orderItems.stream().filter(i -> !i.isService()).findFirst().orElseThrow();
        OrderItem shippingItem = orderItems.stream().filter(OrderItem::isService).findFirst().orElseThrow();
        assertEquals("SKU-1", productItem.getExternalItemId());
        assertNull(shippingItem.getExternalItemId());
    }

    @Test
    void storesTheMarketplaceKeyVerbatimEvenWhenItIsLowercase() {
        // given: pimId-shaped seller SKU — UniqueIdentifierGenerator emits 10 lowercase alphanumerics
        when(pimCatalog.findByMpn(anyString())).thenReturn(Optional.empty());
        Store store = new Store();
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        MarketplaceOrder order = orderWithProduct("k7m2xq9pz4");

        // when
        importer.importOrder(store, "Allegro", order);

        // then: the key must survive byte-for-byte, because Allegro sends it back raw on returns
        OrderItem productItem = capturedOrderItems().stream().filter(i -> !i.isService()).findFirst().orElseThrow();
        assertEquals("k7m2xq9pz4", productItem.getExternalItemId());
    }

    private static MarketplaceOrder orderWithProduct(String manufacturerCode) {
        MarketplaceCustomer.Address address = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, address, address);
        MarketplaceProduct product = new MarketplaceProduct("Widget", manufacturerCode, new BigDecimal("100.00"), 2, BigDecimal.ZERO);
        return new MarketplaceOrder("mp-order-1", customer, List.of(product),
                new MarketplaceOrder.Shipping(new BigDecimal("9.99"), "InPost", null, null),
                "BankTransfer", "txn-1");
    }

    private List<OrderItem> capturedOrderItems() {
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ordersManager).saveWithFulfilment(any(), itemsCaptor.capture());
        return itemsCaptor.getValue();
    }
}
