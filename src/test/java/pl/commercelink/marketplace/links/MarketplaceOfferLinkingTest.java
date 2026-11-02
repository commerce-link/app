package pl.commercelink.marketplace.links;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
    private static final ResolvedOffer OFFER =
            new ResolvedOffer("pim-1", "5900000000011", "ab 123", "Procesory", Source.LINK);

    @Mock
    private MarketplaceOfferLinkRepository linkRepository;
    @Mock
    private MarketplaceOfferResolver resolver;
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
        lenient().when(resolver.isInStoreCatalog(STORE, "pim-1")).thenReturn(true);
        lenient().when(resolver.resolve(STORE, EMPIK, KEY, null)).thenReturn(Optional.of(OFFER));
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
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "pim-1", "user-1");

        // then
        ArgumentCaptor<MarketplaceOfferLink> saved = ArgumentCaptor.forClass(MarketplaceOfferLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().getStoreId()).isEqualTo(STORE);
        assertThat(saved.getValue().getMarketplace()).isEqualTo(EMPIK);
        assertThat(saved.getValue().getSellerOfferKey()).isEqualTo(KEY);
        assertThat(saved.getValue().getPimId()).isEqualTo("pim-1");
        assertThat(saved.getValue().getSource()).isEqualTo(MarketplaceOfferLinkSource.MANUAL);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo("user-1");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        assertThat(item.getSku()).isEqualTo("AB123");
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
    void linkSavesTheLinkButReportsAProductItCannotRecogniseYet() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem item = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(item);
        when(resolver.resolve(STORE, EMPIK, KEY, null)).thenReturn(Optional.empty());

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "pim-1", "user-1");

        // then
        verify(linkRepository).save(any(MarketplaceOfferLink.class));
        verify(orderItemsRepository, never()).save(any());
        assertThat(result.productRecognised()).isFalse();
        assertThat(result.updatedItems()).isZero();
        assertThat(item.isUnlinkedMarketplaceItem()).isTrue();
    }

    @Test
    void aRecognisedProductIsReportedAsSuch() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        unlinkedItem("o1", "i1", FulfilmentStatus.New);
        openOrders(order);

        // when
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "pim-1", "user-1");

        // then
        assertThat(result.productRecognised()).isTrue();
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
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "pim-1", "user-1");

        // then
        assertThat(result.updatedItems()).isEqualTo(3);
        assertThat(List.of(chosen, sameOrderTwin, otherOrderTwin)).allSatisfy(item -> {
            assertThat(item.getSku()).isEqualTo("AB123");
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
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "pim-1", "user-1");

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
        when(resolver.isInStoreCatalog(STORE, "pim-foreign")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> linking.link(STORE, order, "i1", "pim-foreign", "user-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(MarketplaceOfferLinking.PRODUCT_NOT_IN_CATALOG);
        verify(linkRepository, never()).save(any());
        verify(orderItemsRepository, never()).save(any());
    }

    @Test
    void linkRejectsAnItemWithoutAnOfferKey() {
        // given
        Order order = marketplaceOrder("o1", EMPIK, OrderStatus.New, 1);
        OrderItem legacy = unlinkedItem("o1", "i1", FulfilmentStatus.New);
        legacy.setMarketplaceOfferKey(null);
        when(orderItemsRepository.findById("o1", "i1")).thenReturn(legacy);

        // when / then
        assertThatThrownBy(() -> linking.link(STORE, order, "i1", "pim-1", "user-1"))
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
        assertThatThrownBy(() -> linking.link(STORE, order, "i1", "pim-1", "user-1"))
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
        MarketplaceOfferLinking.LinkResult result = linking.link(STORE, order, "i1", "pim-1", "user-1");

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
