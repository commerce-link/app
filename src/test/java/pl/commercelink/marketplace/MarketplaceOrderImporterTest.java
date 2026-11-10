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
import pl.commercelink.externaloffers.ExternalOfferResolver;
import pl.commercelink.externaloffers.ResolvedOffer;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.orders.BillingDetails;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrdersManager;
import pl.commercelink.orders.ShippingDetails;
import pl.commercelink.carriers.CarrierDictionary;
import pl.commercelink.carriers.CarrierTranslator;
import pl.commercelink.carriers.InMemoryCarrierDictionaries;
import pl.commercelink.stores.FulfilmentConfiguration;
import pl.commercelink.stores.Store;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
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
    private CarrierTranslator carrierTranslator = new InMemoryCarrierDictionaries()
            .with(CarrierDictionary.marketplaceOrderCarrierTexts("Morele"), "2=InPost", "6=Zabka", "4=Orlen")
            .translator();

    @Mock
    private OrdersManager ordersManager;

    @Mock
    private ExternalOfferResolver offerResolver;
    @Mock
    private ExternalOfferResolver.StoreOffers storeOffers;

    @InjectMocks
    private MarketplaceOrderImporter importer;

    @BeforeEach
    void linesResolveThroughTheStoresOffers() {
        when(offerResolver.forStore(any())).thenReturn(storeOffers);
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
    void storesOurCarrierNameInsteadOfTheMarketplaceCode() {
        // when / then
        assertEquals("InPost", importer.carrierFor("Morele", "2"));
        assertEquals("Zabka", importer.carrierFor("Morele", "6"));
    }

    @Test
    void keepsTheMarketplaceValueWhenNothingMapsIt() {
        // when / then
        assertEquals("99", importer.carrierFor("Morele", "99"));
        assertEquals("2", importer.carrierFor("Empik", "2"));
        assertNull(importer.carrierFor("Morele", null));
    }

    @Test
    void doesNotReadADigitCodeInsideALongerOne() {
        // when / then
        assertEquals("14", importer.carrierFor("Morele", "14"));
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
    void fillsALinkedLineWithItsEanAndTheTaxonomyCategory() {
        // given
        when(storeOffers.resolve("Empik", "PS-8001"))
                .thenReturn(Optional.of(new ResolvedOffer(null, "2000000001012", null, "Lampy", ResolvedOffer.Source.LINK)));

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("DMW-2001", "PS-8001"));

        // then
        OrderItem item = productItem();
        assertEquals("2000000001012", item.getSku());
        assertNull(item.getManufacturerCode());
        assertEquals("2000000001012", item.getEan());
        assertEquals("Lampy", item.getCategory());
        assertEquals("DMW-2001", item.getExternalItemId());
        assertEquals("PS-8001", item.getExternalOfferKey());
    }

    @Test
    void fillsALinkedLineWithTheLinksEansOnly() {
        // given a link to two EANs of the same goods, which need not be in a catalog
        when(storeOffers.resolve("Empik", "PS-1")).thenReturn(Optional.of(new ResolvedOffer(null,
                "2000000001012", null, null, ResolvedOffer.Source.LINK, List.of("2000000001029"))));

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("DMW-2001", "PS-1"));

        // then fulfilment looks the goods up under either EAN and fills the manufacturer code itself
        OrderItem item = productItem();
        assertEquals("2000000001012:2000000001029", item.getSku());
        assertEquals("2000000001012", item.getEan());
        assertNull(item.getManufacturerCode());
        assertEquals(Categories.UNCATEGORIZED, item.getCategory());
        assertEquals("DMW-2001", item.getExternalItemId());
        assertFalse(item.hasUnlinkedExternalOffer());
    }

    @Test
    void anOwnOfferLineTakesItsEanAsTheSkuAndKeepsItsManufacturerCode() {
        // given
        when(storeOffers.resolve("Empik", "k7m2xq9pz4"))
                .thenReturn(Optional.of(new ResolvedOffer("k7m2xq9pz4", "2000000001012", "MFN-2001", "Lampy", ResolvedOffer.Source.OWN_OFFER)));

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("DMW-2001", "k7m2xq9pz4"));

        // then
        OrderItem item = productItem();
        assertEquals("2000000001012", item.getSku());
        assertEquals("2000000001012", item.getEan());
        assertEquals("MFN-2001", item.getManufacturerCode());
        assertEquals("DMW-2001", item.getExternalItemId());
        assertFalse(item.hasUnlinkedExternalOffer());
    }

    @Test
    void aResolvedLineWithoutAnEanKeepsItsManufacturerCodeAsTheSku() {
        // given the PIM entry of the own offer has no GTIN
        when(storeOffers.resolve("Allegro", "pim-1"))
                .thenReturn(Optional.of(new ResolvedOffer("pim-1", null, "ab 12", "Monitors", ResolvedOffer.Source.OWN_OFFER)));

        // when
        importer.importOrder(storeWithId("store-1"), "Allegro", orderWithProduct("ab 12", "pim-1"));

        // then
        OrderItem item = productItem();
        assertEquals("AB12", item.getSku());
        assertNull(item.getEan());
        assertEquals("Monitors", item.getCategory());
    }

    @Test
    void leavesItemUncategorizedAndRemembersOfferKeyWhenUnresolved() {
        // given
        when(storeOffers.resolve(anyString(), anyString())).thenReturn(Optional.empty());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("DMW-2001", "PS-8001"));

        // then
        OrderItem item = productItem();
        assertEquals(Categories.UNCATEGORIZED, item.getCategory());
        assertEquals("DMW-2001", item.getSku());
        assertEquals("PS-8001", item.getExternalOfferKey());
        assertTrue(item.hasUnlinkedExternalOffer());
    }

    @Test
    void resolvesTwoLinesWithTheSameOfferKey() {
        // given
        when(storeOffers.resolve(anyString(), anyString()))
                .thenReturn(Optional.of(new ResolvedOffer(null, "2000000001012", null, "Lampy", ResolvedOffer.Source.LINK)));
        MarketplaceOrder base = orderWithProduct("DMW-2001", "PS-8001");
        MarketplaceProduct first = base.products().get(0);
        MarketplaceOrder order = new MarketplaceOrder(base.externalOrderId(), base.customer(), List.of(first, first),
                base.shipping(), base.paymentType(), base.paymentTransactionId());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", order);

        // then
        List<OrderItem> products = capturedOrderItems().stream().filter(i -> !i.isService()).toList();
        assertEquals(2, products.size());
        assertEquals("2000000001012", products.get(0).getSku());
        assertEquals("2000000001012", products.get(1).getSku());
    }

    @Test
    void anAlreadyImportedOrderIsNeitherResolvedNorSavedAgain() {
        // given
        when(ordersManager.isAlreadyImported("store-1", "mp-order-1")).thenReturn(true);

        // when
        boolean imported = importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("DMW-2001", "PS-8001"));

        // then
        assertFalse(imported);
        // the store scope reads the catalogs lazily, so creating it costs nothing; no line is resolved
        verifyNoInteractions(storeOffers);
        verify(ordersManager, never()).saveWithFulfilment(any(), any());
    }

    @Test
    void importsOneFetchInOneStoreScopeForAllItsOrders() {
        // given three orders of one fetch, one of them already imported
        MarketplaceOrder first = renamed(orderWithProduct("DMW-2001", "PS-1"), "mp-order-1");
        MarketplaceOrder second = renamed(orderWithProduct("DMW-2002", "PS-2"), "mp-order-2");
        MarketplaceOrder known = renamed(orderWithProduct("DMW-2003", "PS-3"), "mp-order-3");
        when(ordersManager.isAlreadyImported("store-1", "mp-order-3")).thenReturn(true);
        when(ordersManager.saveWithFulfilment(any(), any())).thenReturn(true);

        // when
        int imported = importer.importOrders(storeWithId("store-1"), "Empik", List.of(first, second, known));

        // then
        assertEquals(2, imported);
        verify(offerResolver, times(1)).forStore("store-1");
        verify(storeOffers).resolve("Empik", "PS-1");
        verify(storeOffers).resolve("Empik", "PS-2");
        verify(storeOffers, never()).resolve("Empik", "PS-3");
    }

    @Test
    void resolvesAllLinesOfAnOrderInOneStoreScope() {
        // given
        when(storeOffers.resolve(anyString(), anyString()))
                .thenReturn(Optional.of(new ResolvedOffer(null, "2000000001012", null, "Lampy", ResolvedOffer.Source.LINK)));
        MarketplaceOrder base = orderWithProduct("DMW-2001", "PS-8001");
        MarketplaceProduct first = base.products().get(0);
        MarketplaceProduct second = new MarketplaceProduct("Other", "DMW-2002", new BigDecimal("50.00"), 1,
                BigDecimal.ZERO, "PS-8002");
        MarketplaceOrder order = new MarketplaceOrder(base.externalOrderId(), base.customer(), List.of(first, second),
                base.shipping(), base.paymentType(), base.paymentTransactionId());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", order);

        // then
        verify(offerResolver, times(1)).forStore("store-1");
        verify(storeOffers, times(2)).resolve(anyString(), anyString());
    }

    @Test
    void aBlankOfferKeyIsStoredAsNoKey() {
        // given
        when(storeOffers.resolve("Empik", null)).thenReturn(Optional.empty());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", orderWithProduct("DMW-2001", "   "));

        // then
        OrderItem item = productItem();
        assertNull(item.getExternalOfferKey());
        assertFalse(item.hasUnlinkedExternalOffer());
    }

    @Test
    void storesTheResolutionSourceOfEachLine() {
        // given three lines: a link, an own offer and one nothing recognised
        when(storeOffers.resolve("Empik", "K1")).thenReturn(Optional.of(
                new ResolvedOffer(null, "2000000001012", null, null, ResolvedOffer.Source.LINK)));
        when(storeOffers.resolve("Empik", "K2")).thenReturn(Optional.of(
                new ResolvedOffer("K2", "2000000001029", "M2", "Lampy", ResolvedOffer.Source.OWN_OFFER)));
        when(storeOffers.resolve("Empik", "K3")).thenReturn(Optional.empty());
        MarketplaceOrder base = orderWithProduct("C1", "K1");
        List<MarketplaceProduct> products = List.of(
                new MarketplaceProduct("Widget 1", "C1", new BigDecimal("100.00"), 1, BigDecimal.ZERO, "K1"),
                new MarketplaceProduct("Widget 2", "C2", new BigDecimal("100.00"), 1, BigDecimal.ZERO, "K2"),
                new MarketplaceProduct("Widget 3", "C3", new BigDecimal("100.00"), 1, BigDecimal.ZERO, "K3"));
        MarketplaceOrder order = new MarketplaceOrder(base.externalOrderId(), base.customer(), products,
                base.shipping(), base.paymentType(), base.paymentTransactionId());

        // when
        importer.importOrder(storeWithId("store-1"), "Empik", order);

        // then
        Map<String, ResolvedOffer.Source> sourceByKey = new HashMap<>();
        capturedOrderItems().stream().filter(i -> !i.isService())
                .forEach(i -> sourceByKey.put(i.getExternalOfferKey(), i.getExternalOfferSource()));
        assertEquals(ResolvedOffer.Source.LINK, sourceByKey.get("K1"));
        assertEquals(ResolvedOffer.Source.OWN_OFFER, sourceByKey.get("K2"));
        assertNull(sourceByKey.get("K3"));
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
