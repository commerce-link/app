package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.marketplace.links.ResolvedOffer.Source;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.taxonomy.Categories;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceOfferLinkingTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";
    private static final String KEY = "SILLO-84980";
    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");
    private static final String EAN = "5900000000011";
    private static final ResolvedOffer OFFER =
            new ResolvedOffer(null, EAN, "ab 123", "Procesory", Source.LINK);

    @Mock
    private MarketplaceOfferLinkRepository linkRepository;
    @Mock
    private MarketplaceOfferResolver resolver;
    @Mock
    private MarketplaceOfferResolver.StoreOffers storeOffers;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;

    private MarketplaceOfferLinking linking;
    private final Map<String, List<OrderItem>> itemsByOrder = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        linking = new MarketplaceOfferLinking(linkRepository, resolver, ordersRepository, orderItemsRepository,
                Clock.fixed(NOW, ZoneId.of("Europe/Warsaw")));
        lenient().when(resolver.forStore(STORE)).thenReturn(storeOffers);
        lenient().when(storeOffers.catalogProducts()).thenReturn(List.of(
                catalogProduct("c1", "p1", null, EAN, "ab 123"),
                catalogProduct("c1", "p2", "pim-2", null, "NO-EAN"),
                catalogProduct("c1", "p3", null, "5909999999999", " ")));
        lenient().when(storeOffers.resolve(EMPIK, KEY, null)).thenReturn(Optional.of(OFFER));
        lenient().when(orderItemsRepository.findByOrderIds(anyList())).thenAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            Map<String, List<OrderItem>> found = new LinkedHashMap<>();
            ids.forEach(id -> found.put(id, itemsByOrder.getOrDefault(id, List.of())));
            return found;
        });
    }

    @Test
    void linkSavesManualLinkAndUpdatesTheItem() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        openOrders(order);

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "c1", "p1", "user-1");

        // then
        ArgumentCaptor<MarketplaceOfferLink> saved = ArgumentCaptor.forClass(MarketplaceOfferLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo(STORE);
        assertThat(saved.getValue().getMarketplace()).isEqualTo(EMPIK);
        assertThat(saved.getValue().getSellerOfferKey()).isEqualTo(KEY);
        assertThat(saved.getValue().getEans()).containsExactly(EAN);
        assertThat(saved.getValue().getSource()).isEqualTo(MarketplaceOfferLinkSource.MANUAL);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("user-1");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(item.getSku()).isEqualTo(EAN);
        assertThat(item.getManufacturerCode()).isEqualTo("AB123");
        assertThat(item.getEan()).isEqualTo("5900000000011");
        assertThat(item.getCategory()).isEqualTo("Procesory");
        assertThat(item.getExternalItemId()).isEqualTo("RAW-1");
        assertThat(item.getMarketplaceOfferKey()).isEqualTo(KEY);
        assertThat(item.isUnlinkedMarketplaceItem()).isFalse();
        verify(orderItemsRepository).save(item);
        assertThat(result.updatedItems()).isEqualTo(1);
    }

    @Test
    void linkFillsNothingWhenTheSavedLinkNoLongerLeadsToOneProduct() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(item);
        when(storeOffers.resolve(EMPIK, KEY, null)).thenReturn(Optional.empty());

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "c1", "p1", "user-1");

        // then
        verify(linkRepository).save(any(MarketplaceOfferLink.class));
        verify(orderItemsRepository, never()).save(any());
        assertThat(result.updatedItems()).isZero();
        assertThat(item.isUnlinkedMarketplaceItem()).isTrue();
    }

    @Test
    void linkAlsoUpdatesOtherNewItemsWithTheSameOfferKey() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        Order other = marketplaceOrder("o2", EMPIK, OrderStatus.Assembly, 30);
        Order otherMarketplace = marketplaceOrder("o3", "Allegro", OrderStatus.New, 2);
        Order tooOld = marketplaceOrder("o4", EMPIK, OrderStatus.New, 91);
        OrderItem chosen = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        OrderItem sameOrderTwin = unlinkedItem("o1", "i2", FulfilmentStatus.New);
        OrderItem otherOrderTwin = unlinkedItem("o2", "i3", FulfilmentStatus.New);
        OrderItem differentKey = unlinkedItem("o2", "i4", FulfilmentStatus.New);
        differentKey.setMarketplaceOfferKey("OTHER-1");
        OrderItem sameKeyOnAllegro = unlinkedItem("o3", "i5", FulfilmentStatus.New);
        OrderItem sameKeyTooOld = unlinkedItem("o4", "i6", FulfilmentStatus.New);
        openOrders(order, other, otherMarketplace, tooOld);

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "c1", "p1", "user-1");

        // then
        assertThat(result.updatedItems()).isEqualTo(3);
        assertThat(List.of(chosen, sameOrderTwin, otherOrderTwin)).allSatisfy(item -> {
            assertThat(item.getSku()).isEqualTo(EAN);
            assertThat(item.getCategory()).isEqualTo("Procesory");
            verify(orderItemsRepository).save(item);
        });
        assertThat(List.of(differentKey, sameKeyOnAllegro, sameKeyTooOld)).allSatisfy(item -> {
            assertThat(item.getSku()).isEqualTo("RAW-1");
            assertThat(item.getCategory()).isEqualTo(Categories.UNCATEGORIZED);
            verify(orderItemsRepository, never()).save(item);
        });
    }

    @Test
    void linkSkipsItemsAlreadyAllocated() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem chosen = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        OrderItem allocated = unlinkedItem("o1", "i2", FulfilmentStatus.Allocation);
        OrderItem group = unlinkedItem("o1", "i3", FulfilmentStatus.New);
        group.setSku("#1xA|1xB");
        openOrders(order);

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "c1", "p1", "user-1");

        // then
        verify(linkRepository).save(any());
        assertThat(result.updatedItems()).isEqualTo(1);
        assertThat(chosen.getCategory()).isEqualTo("Procesory");
        assertThat(allocated.getSku()).isEqualTo("RAW-1");
        assertThat(allocated.getCategory()).isEqualTo(Categories.UNCATEGORIZED);
        assertThat(group.getSku()).isEqualTo("#1xA|1xB");
        verify(orderItemsRepository, never()).save(allocated);
        verify(orderItemsRepository, never()).save(group);
    }

    @Test
    void linkRejectsProductOutsideTheStoreCatalog() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(item);

        // when / then
        assertThatThrownBy(() -> linking.link(STORE, order, "i1", "other-store-category", "p1", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MarketplaceOfferLinking.PRODUCT_NOT_IN_CATALOG);
        verify(linkRepository, never()).save(any());
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    void linkRejectsACatalogProductWithoutAnEan() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(item);

        // when / then
        assertThatThrownBy(() -> linking.link(STORE, order, "i1", "c1", "p2", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MarketplaceOfferLinking.PRODUCT_NOT_IN_CATALOG);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void aCatalogProductWithoutAManufacturerCodeIsLinkedThroughItsEan() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        openOrders(order);
        when(storeOffers.resolve(EMPIK, KEY, null)).thenReturn(Optional.of(
                new ResolvedOffer(null, "5909999999999", null, "Procesory", Source.LINK)));

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "c1", "p3", "user-1");

        // then
        ArgumentCaptor<MarketplaceOfferLink> saved = ArgumentCaptor.forClass(MarketplaceOfferLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().getEans()).containsExactly("5909999999999");
        assertThat(result.updatedItems()).isEqualTo(1);
        assertThat(item.getSku()).isEqualTo("5909999999999");
        assertThat(item.getManufacturerCode()).isNull();
    }

    @Test
    void aProductWithoutAPimIdIsLinkedThroughItsEan() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        openOrders(order);

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "c1", "p1", "user-1");

        // then
        ArgumentCaptor<MarketplaceOfferLink> saved = ArgumentCaptor.forClass(MarketplaceOfferLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().getEans()).containsExactly(EAN);
        assertThat(result.updatedItems()).isEqualTo(1);
        assertThat(item.getSku()).isEqualTo(EAN);
        assertThat(item.getEan()).isEqualTo(EAN);
    }

    @Test
    void aProductWhoseEanAnotherCatalogProductSharesIsLinkedAsTheSameGoods() {
        // given the store's real catalog, read through the resolver: two products share the EAN under different codes
        ProductRepository productRepository = Mockito.mock(ProductRepository.class);
        ProductCatalogRepository catalogRepository = Mockito.mock(ProductCatalogRepository.class);
        CategoryDefinition category = new CategoryDefinition();
        category.setCategoryId("c1");
        category.setName("Pościel");
        ProductCatalog catalog = new ProductCatalog();
        catalog.setCatalogId("main");
        catalog.setCategories(List.of(category));
        when(catalogRepository.findAll(STORE)).thenReturn(List.of(catalog));
        Product white = catalogProduct("c1", "p-white", null, EAN, "PB-B");
        Product grey = catalogProduct("c1", "p-grey", null, " " + EAN + " ", "PB-S");
        when(productRepository.findAll("c1")).thenReturn(List.of(white, grey));
        when(linkRepository.find(STORE, EMPIK, KEY)).thenReturn(Optional.of(MarketplaceOfferLink.of(STORE, EMPIK, KEY,
                List.of(EAN), MarketplaceOfferLinkSource.MANUAL, "user-1", NOW)));
        MarketplaceOfferResolver realResolver = new MarketplaceOfferResolver(linkRepository,
                Mockito.mock(PimCatalog.class), Mockito.mock(StorePimAliases.class), productRepository, catalogRepository);
        MarketplaceOfferLinking realLinking = new MarketplaceOfferLinking(linkRepository, realResolver, ordersRepository,
                orderItemsRepository, Clock.fixed(NOW, ZoneId.of("Europe/Warsaw")));
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(item);

        // when
        MarketplaceOfferLinking.LinkResult result = realLinking.link(STORE, order, "i1", "c1", "p-white", "user-1");

        // then fulfilment looks the goods up by the EAN, whichever of the two products the item names
        verify(linkRepository).save(any(MarketplaceOfferLink.class));
        assertThat(result.updatedItems()).isEqualTo(1);
        assertThat(item.getSku()).isEqualTo(EAN);
        assertThat(item.getEan()).isEqualTo(EAN);
        assertThat(item.getManufacturerCode()).isEqualTo("PB-S");
        assertThat(item.getCategory()).isEqualTo("Pościel");
    }

    @Test
    void linkRejectsAnItemWithoutAnOfferKey() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem legacy = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        legacy.setMarketplaceOfferKey(null);
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(legacy);

        // when / then
        assertThatThrownBy(() -> linking.link(STORE, order, "i1", "c1", "p1", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MarketplaceOfferLinking.NO_OFFER_KEY);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void linkRejectsAnOrderThatDidNotComeFromAMarketplace() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        order.setSource(new OrderSource("Sklep", OrderSourceType.WebStore));
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(unlinkedItem("o1", "i1", FulfilmentStatus.New));

        // when / then
        assertThatThrownBy(() -> linking.link(STORE, order, "i1", "c1", "p1", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MarketplaceOfferLinking.NOT_A_MARKETPLACE_ORDER);
        verify(linkRepository, never()).save(any());
    }

    @Test
    void theChosenItemIsFilledEvenWhenItsOrderIsOlderThanTheSweep() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 200);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        openOrders(order);

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "c1", "p1", "user-1");

        // then
        assertThat(result.updatedItems()).isEqualTo(1);
        verify(orderItemsRepository).save(item);
    }

    private void openOrders(Order... orders) {
        when(ordersRepository.findByStoreAndStatuses(eq(STORE), anyCollection())).thenReturn(new ArrayList<>(List.of(orders)));
        for (Order order : orders) {
            for (OrderItem item : itemsByOrder.getOrDefault(order.getOrderId(), List.of())) {
                lenient().when(orderItemsRepository.findById(order.getOrderId(), item.getItemId())).thenReturn(item);
            }
        }
    }

    private static Product catalogProduct(String categoryId, String productId, String pimId, String ean, String mfn) {
        Product product = new Product(categoryId, pimId, ean, mfn, "Brand", "Brand", "Product " + productId, "Default");
        product.setProductId(productId);
        return product;
    }

    private Order marketplaceOrder(String orderId, String marketplace, OrderStatus status, int daysAgo) {
        Order order = new Order();
        order.setStoreId(STORE);
        order.setOrderId(orderId);
        order.setStatus(status);
        order.setExternalOrderId("ext-" + orderId);
        order.setSource(new OrderSource(marketplace, OrderSourceType.Marketplace));
        order.setOrderedAt(LocalDateTime.ofInstant(NOW, ZoneId.of("Europe/Warsaw")).minusDays(daysAgo));
        return order;
    }

    private OrderItem unlinkedItem(String orderId, String itemId, FulfilmentStatus status) {
        OrderItem item = new OrderItem(orderId, Categories.UNCATEGORIZED, "Offer " + itemId, 1, 100, "RAW-1", false);
        item.setItemId(itemId);
        item.setStatus(status);
        item.setExternalItemId("RAW-1");
        item.setMarketplaceOfferKey(KEY);
        itemsByOrder.computeIfAbsent(orderId, id -> new ArrayList<>()).add(item);
        return item;
    }
}
