package pl.commercelink.marketplace;

import org.junit.jupiter.api.BeforeEach;
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
import pl.commercelink.marketplace.links.MarketplaceOfferResolver;
import pl.commercelink.marketplace.links.ResolvedOffer;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.shipping.CarrierDictionary;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Integration;
import pl.commercelink.stores.IntegrationType;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceOrderImporterTest {

    @Spy
    private CarrierDictionary carrierDictionary = dictionaryForMorele();

    @Mock
    private OrdersManager ordersManager;

    @Mock
    private MarketplaceOfferResolver offerResolver;
    @Mock
    private MarketplaceOfferResolver.StoreOffers storeOffers;

    @InjectMocks
    private MarketplaceOrderImporter importer;

    @BeforeEach
    void linesResolveThroughTheStoresOffers() {
        when(offerResolver.forStore(any())).thenReturn(storeOffers);
    }

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
    void takesTheLastWordOfTheShippingNameAsTheSurname() {
        // given
        MarketplaceCustomer.Address shippingAddress = new MarketplaceCustomer.Address(
                "Anna Maria Kowalska", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Anna Maria Kowalska", null, "anna@example.com",
                "500600700", null, shippingAddress, shippingAddress);

        // when
        ShippingDetails shipping = importer.toShippingDetails(customer);

        // then
        assertEquals("Anna Maria", shipping.getName());
        assertEquals("Kowalska", shipping.getSurname());
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
    void splitsAFirstNameAndSurname() {
        // given
        MarketplaceCustomer.Address billingAddress = new MarketplaceCustomer.Address(
                "Jan Kowalski", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Jan Kowalski", null, "jan@example.com",
                "500600700", null, billingAddress, billingAddress);

        // when
        BillingDetails billing = importer.toBillingDetails(customer);

        // then
        assertEquals("Jan", billing.getName());
        assertEquals("Kowalski", billing.getSurname());
    }

    @Test
    void takesTheLastWordOfTheBillingNameAsTheSurname() {
        // given
        MarketplaceCustomer.Address billingAddress = new MarketplaceCustomer.Address(
                "Anna Maria Kowalska", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Anna Maria Kowalska", null, "anna@example.com",
                "500600700", null, billingAddress, billingAddress);

        // when
        BillingDetails billing = importer.toBillingDetails(customer);

        // then
        assertEquals("Anna Maria", billing.getName());
        assertEquals("Kowalska", billing.getSurname());
    }

    @Test
    void keepsASingleWordNameAsTheFirstNameWithoutASurname() {
        // given
        MarketplaceCustomer.Address billingAddress = new MarketplaceCustomer.Address(
                "Kowalska", "500600700", "Prosta 1", "00-001", "Warszawa", "Polska");
        MarketplaceCustomer customer = new MarketplaceCustomer(
                MarketplaceCustomer.CustomerType.INDIVIDUAL, "Kowalska", null, "anna@example.com",
                "500600700", null, billingAddress, billingAddress);

        // when
        BillingDetails billing = importer.toBillingDetails(customer);

        // then
        assertEquals("Kowalska", billing.getName());
        assertEquals("", billing.getSurname());
    }

    @Test
    void setsTheExternalItemIdOnProductItemsButNotOnTheShippingItem() {
        // given
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
        Store store = new Store();
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        MarketplaceOrder order = orderWithProduct("k7m2xq9pz4");

        // when
        importer.importOrder(store, "Allegro", order);

        // then: the key must survive byte-for-byte, because Allegro sends it back raw on returns
        OrderItem productItem = capturedOrderItems().stream().filter(i -> !i.isService()).findFirst().orElseThrow();
        assertEquals("k7m2xq9pz4", productItem.getExternalItemId());
    }

    @Test
    void storesTheMarketplaceShippingDateAsThePreferredOne() {
        Store store = new Store();
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        MarketplaceOrder base = orderWithProduct("SKU-1");
        MarketplaceOrder marketplaceOrder = new MarketplaceOrder(base.externalOrderId(), base.customer(), base.products(),
                new MarketplaceOrder.Shipping(new BigDecimal("9.99"), "InPost", null, LocalDate.of(2026, 9, 10)),
                base.paymentType(), base.paymentTransactionId());

        importer.importOrder(store, "Allegro", marketplaceOrder);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(ordersManager).saveWithFulfilment(orderCaptor.capture(), any());
        assertEquals(LocalDate.of(2026, 9, 10), orderCaptor.getValue().getPreferredShippingAt());
        assertNull(orderCaptor.getValue().getEstimatedShippingAt());
    }

    @Test
    void usesResolvedProductCodesAndCategory() {
        // given
        when(storeOffers.resolve("Empik", "SILLO-84980", "4M-7041"))
                .thenReturn(Optional.of(new ResolvedOffer("pim-1", "5903771700818", "7041", "Zabawki", ResolvedOffer.Source.LINK)));

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("4M-7041", "SILLO-84980"));

        // then
        OrderItem item = productItem();
        assertEquals("7041", item.getSku());
        assertEquals("5903771700818", item.getEan());
        assertEquals("Zabawki", item.getCategory());
        assertEquals("4M-7041", item.getExternalItemId());
        assertEquals("SILLO-84980", item.getMarketplaceOfferKey());
    }

    @Test
    void fillsALinkedLineFromTheCatalogProduct() {
        // given
        when(storeOffers.resolve("Empik", "SILLO-84980", "4M-7041"))
                .thenReturn(Optional.of(new ResolvedOffer(null, "5903771700818", "ab 12", "Krzesła", ResolvedOffer.Source.LINK)));

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("4M-7041", "SILLO-84980"));

        // then
        OrderItem item = productItem();
        assertEquals("AB12", item.getSku());
        assertEquals("AB12", item.getManufacturerCode());
        assertEquals("5903771700818", item.getEan());
        assertEquals("Krzesła", item.getCategory());
        assertFalse(item.isUnlinkedMarketplaceItem());
    }

    @Test
    void leavesTheManufacturerCodeOfOwnOfferAndManufacturerCodeLinesUnset() {
        // given
        when(storeOffers.resolve("Empik", "k7m2xq9pz4", "4M-7041"))
                .thenReturn(Optional.of(new ResolvedOffer("k7m2xq9pz4", "5903771700818", "7041", "Zabawki", ResolvedOffer.Source.OWN_OFFER)));
        when(storeOffers.resolve("Allegro", null, "ab 12"))
                .thenReturn(Optional.of(new ResolvedOffer("pim-1", "5903771700818", "ab 12", "Monitors", ResolvedOffer.Source.MFN)));

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("4M-7041", "k7m2xq9pz4"));
        importer.importOrder(storeWithId("store-1"), "Allegro", orderWithProduct("ab 12"));

        // then
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        verify(ordersManager, times(2)).saveWithFulfilment(any(), itemsCaptor.capture());
        List<OrderItem> lines = itemsCaptor.getAllValues().stream()
                .flatMap(List::stream).filter(i -> !i.isService()).toList();
        assertEquals(2, lines.size());
        assertNull(lines.get(0).getManufacturerCode());
        assertNull(lines.get(1).getManufacturerCode());
    }

    @Test
    void keepsTodaysBehaviourWithoutSellerOfferKey() {
        // given
        when(storeOffers.resolve("Allegro", null, "ab 12"))
                .thenReturn(Optional.of(new ResolvedOffer("pim-1", "5903771700818", "ab 12", "Monitors", ResolvedOffer.Source.MFN)));

        // when
        importer.importOrder(storeWithId("store-1"), "Allegro", orderWithProduct("ab 12"));

        // then
        OrderItem item = productItem();
        assertEquals("AB12", item.getSku());
        assertNull(item.getEan());
        assertEquals("Monitors", item.getCategory());
        assertEquals("ab 12", item.getExternalItemId());
        assertNull(item.getMarketplaceOfferKey());
        assertFalse(item.isUnlinkedMarketplaceItem());
    }

    @Test
    void leavesItemUncategorizedAndRemembersOfferKeyWhenUnresolved() {
        // given
        when(storeOffers.resolve(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("4M-7041", "SILLO-84980"));

        // then
        OrderItem item = productItem();
        assertEquals(Categories.UNCATEGORIZED, item.getCategory());
        assertEquals("4M-7041", item.getSku());
        assertEquals("SILLO-84980", item.getMarketplaceOfferKey());
        assertTrue(item.isUnlinkedMarketplaceItem());
    }

    @Test
    void resolvesTwoLinesWithTheSameOfferKey() {
        // given
        when(storeOffers.resolve(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new ResolvedOffer("pim-1", "5903771700818", "7041", "Zabawki", ResolvedOffer.Source.LINK)));
        MarketplaceOrder base = orderWithProduct("4M-7041", "SILLO-84980");
        MarketplaceProduct first = base.products().get(0);
        MarketplaceOrder order = new MarketplaceOrder(base.externalOrderId(), base.customer(), List.of(first, first),
                base.shipping(), base.paymentType(), base.paymentTransactionId());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", order);

        // then
        List<OrderItem> products = capturedOrderItems().stream().filter(i -> !i.isService()).toList();
        assertEquals(2, products.size());
        assertEquals("7041", products.get(0).getSku());
        assertEquals("7041", products.get(1).getSku());
    }

    @Test
    void anAlreadyImportedOrderIsNeitherResolvedNorSavedAgain() {
        // given
        when(ordersManager.isAlreadyImported("store-1", "mp-order-1")).thenReturn(true);

        // when
        boolean imported = importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("4M-7041", "SILLO-84980"));

        // then
        assertFalse(imported);
        // the store scope reads the catalogs lazily, so creating it costs nothing; no line is resolved
        verifyNoInteractions(storeOffers);
        verify(ordersManager, never()).saveWithFulfilment(any(), any());
    }

    @Test
    void importsOneFetchReadingTheCatalogsOnceForAllItsOrders() {
        // given three orders of one fetch, one of them already imported
        MarketplaceOrder first = renamed(orderWithProduct("4M-7041", "SILLO-1"), "mp-order-1");
        MarketplaceOrder second = renamed(orderWithProduct("4M-7042", "SILLO-2"), "mp-order-2");
        MarketplaceOrder known = renamed(orderWithProduct("4M-7043", "SILLO-3"), "mp-order-3");
        when(ordersManager.isAlreadyImported("store-1", "mp-order-3")).thenReturn(true);
        when(ordersManager.saveWithFulfilment(any(), any())).thenReturn(true);

        // when
        int imported = importer.importOrders(storeWithId("store-1"), "Empik", List.of(first, second, known));

        // then
        assertEquals(2, imported);
        verify(offerResolver, times(1)).forStore("store-1");
        verify(storeOffers).resolve("Empik", "SILLO-1", "4M-7041");
        verify(storeOffers).resolve("Empik", "SILLO-2", "4M-7042");
        verify(storeOffers, never()).resolve("Empik", "SILLO-3", "4M-7043");
    }

    @Test
    void resolvesAllLinesOfAnOrderInOneStoreScope() {
        // given
        when(storeOffers.resolve(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new ResolvedOffer("pim-1", "5903771700818", "7041", "Zabawki", ResolvedOffer.Source.LINK)));
        MarketplaceOrder base = orderWithProduct("4M-7041", "SILLO-84980");
        MarketplaceProduct first = base.products().get(0);
        MarketplaceProduct second = new MarketplaceProduct("Other", "4M-7042", new BigDecimal("50.00"), 1,
                BigDecimal.ZERO, "SILLO-84981");
        MarketplaceOrder order = new MarketplaceOrder(base.externalOrderId(), base.customer(), List.of(first, second),
                base.shipping(), base.paymentType(), base.paymentTransactionId());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", order);

        // then
        verify(offerResolver, times(1)).forStore("store-1");
        verify(storeOffers, times(2)).resolve(anyString(), anyString(), anyString());
    }

    @Test
    void aBlankOfferKeyIsStoredAsNoKey() {
        // given
        when(storeOffers.resolve("Empik", null, "4M-7041")).thenReturn(Optional.empty());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("4M-7041", "   "));

        // then
        OrderItem item = productItem();
        assertNull(item.getMarketplaceOfferKey());
        assertFalse(item.isUnlinkedMarketplaceItem());
    }

    private OrderItem productItem() {
        return capturedOrderItems().stream().filter(i -> !i.isService()).findFirst().orElseThrow();
    }

    private static Store storeWithId(String storeId) {
        Store store = new Store();
        store.setStoreId(storeId);
        store.setFulfilmentConfiguration(new FulfilmentConfiguration());
        return store;
    }

    private static MarketplaceOrder orderWithProduct(String manufacturerCode, String sellerOfferKey) {
        MarketplaceOrder base = orderWithProduct(manufacturerCode);
        MarketplaceProduct product = new MarketplaceProduct("Widget", manufacturerCode, new BigDecimal("100.00"), 2,
                BigDecimal.ZERO, sellerOfferKey);
        return new MarketplaceOrder(base.externalOrderId(), base.customer(), List.of(product), base.shipping(),
                base.paymentType(), base.paymentTransactionId());
    }

    private static MarketplaceOrder renamed(MarketplaceOrder order, String externalOrderId) {
        return new MarketplaceOrder(externalOrderId, order.customer(), order.products(), order.shipping(),
                order.paymentType(), order.paymentTransactionId());
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
