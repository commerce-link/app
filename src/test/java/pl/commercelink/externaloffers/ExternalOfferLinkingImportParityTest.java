package pl.commercelink.externaloffers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.orders.FulfilmentStatus;
import pl.commercelink.orders.Order;
import pl.commercelink.orders.OrderItem;
import pl.commercelink.orders.OrderItemsRepository;
import pl.commercelink.orders.OrderSource;
import pl.commercelink.orders.OrderSourceType;
import pl.commercelink.orders.OrderStatus;
import pl.commercelink.orders.OrdersRepository;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;
import pl.commercelink.products.StoreCategoryResolver;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.products.information.StorePimAliases;
import pl.commercelink.taxonomy.Categories;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * "Link to product" with the real resolver: the item it fills must look exactly like the line the next import of the
 * offer brings, codes and category alike, whatever the item carried before.
 */
@ExtendWith(MockitoExtension.class)
class ExternalOfferLinkingImportParityTest {

    private static final String STORE = "s1";
    private static final String EMPIK = "Empik";
    private static final String KEY = "PS-1001";
    private static final String RAW_CODE = "DMW-1001";
    private static final String TYPED = "2001000000043";
    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");

    @Mock
    private ExternalOfferLinkRepository linkRepository;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private StorePimAliases pimAliases;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private StoreCategoryResolver storeCategoryResolver;
    @Mock
    private OrdersRepository ordersRepository;
    @Mock
    private OrderItemsRepository orderItemsRepository;

    @InjectMocks
    private ExternalOfferResolver resolver;

    private ExternalOfferLinking linking;
    private final AtomicReference<ExternalOfferLink> savedLink = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        linking = new ExternalOfferLinking(linkRepository, resolver, ordersRepository, orderItemsRepository,
                Clock.fixed(NOW, ZoneId.of("Europe/Warsaw")));
        doAnswer(invocation -> {
            savedLink.set(invocation.getArgument(0));
            return null;
        }).when(linkRepository).save(any());
        lenient().when(linkRepository.find(STORE, EMPIK, KEY)).thenAnswer(invocation -> Optional.ofNullable(savedLink.get()));
        lenient().when(ordersRepository.archiveIndex(eq(STORE), any())).thenReturn(Collections.emptyIterator());
        lenient().when(ordersRepository.findByIds(eq(STORE), anyList())).thenReturn(List.of());
        lenient().when(storeCategoryResolver.categoryNamesByPimCategoryId(STORE)).thenReturn(Map.of("pim-7", "Dom i kuchnia"));
    }

    @Test
    void aLinkedItemTakesTheStoresCategoryOfItsEanAsTheNextImportDoes() {
        // given the taxonomy knows the goods, and the item still carries an earlier link's category and fulfilment's code
        when(taxonomyCache.findByEan(TYPED)).thenReturn(new Taxonomy(TYPED, "DMW-K1", "Domowik", "Czajnik", "Kitchen", 1,
                null, null, "raw", "pim-7"));
        Order order = order();
        OrderItem item = itemCarrying(order, "Lampy", "DMW-OLD");

        // when
        linking.linkEan(STORE, order, item.getItemId(), TYPED, "user-1");

        // then
        OrderItem imported = importedLine(order);
        assertThat(imported.getCategory()).isEqualTo("Dom i kuchnia");
        assertSameCodesAndCategory(item, imported);
    }

    @Test
    void aLinkedItemIsUncategorisedWhenTheTaxonomyDoesNotKnowItsEanAsTheNextImportLeavesIt() {
        // given
        Order order = order();
        OrderItem item = itemCarrying(order, "Lampy", "DMW-OLD");

        // when
        linking.linkEan(STORE, order, item.getItemId(), TYPED, "user-1");

        // then
        OrderItem imported = importedLine(order);
        assertThat(imported.getCategory()).isEqualTo(Categories.UNCATEGORIZED);
        assertSameCodesAndCategory(item, imported);
    }

    // what MarketplaceOrderImporter makes of the offer's next line
    private OrderItem importedLine(Order order) {
        ResolvedOffer offer = resolver.forStore(STORE).resolve(EMPIK, KEY, RAW_CODE).orElseThrow();
        OrderItem line = new OrderItem(order.getOrderId(),
                offer.category() != null ? offer.category() : Categories.UNCATEGORIZED, "Czajnik", 1, 100,
                offer.sku(), false);
        line.setExternalItemId(RAW_CODE);
        line.setExternalOfferKey(KEY);
        offer.fill(line);
        return line;
    }

    private static void assertSameCodesAndCategory(OrderItem linked, OrderItem imported) {
        assertThat(linked.getSku()).isEqualTo(imported.getSku()).isEqualTo(TYPED);
        assertThat(linked.getEan()).isEqualTo(imported.getEan());
        assertThat(linked.getManufacturerCode()).isEqualTo(imported.getManufacturerCode()).isNull();
        assertThat(linked.getCategory()).isEqualTo(imported.getCategory());
        assertThat(linked.hasUnlinkedExternalOffer()).isEqualTo(imported.hasUnlinkedExternalOffer()).isFalse();
    }

    private OrderItem itemCarrying(Order order, String category, String manufacturerCode) {
        OrderItem item = new OrderItem(order.getOrderId(), category, "Czajnik", 1, 100, "2001000000050", false);
        item.setItemId("i1");
        item.setStatus(FulfilmentStatus.New);
        item.setExternalItemId(RAW_CODE);
        item.setExternalOfferKey(KEY);
        item.setEan("2001000000050");
        item.setManufacturerCode(manufacturerCode);
        when(orderItemsRepository.findById(order.getOrderId(), "i1")).thenReturn(item);
        when(orderItemsRepository.findByOrderIds(anyList())).thenReturn(Map.of(order.getOrderId(), List.of(item)));
        return item;
    }

    private static Order order() {
        Order order = new Order();
        order.setStoreId(STORE);
        order.setOrderId("o1");
        order.setStatus(OrderStatus.New);
        order.setSource(new OrderSource(EMPIK, OrderSourceType.Marketplace));
        order.setOrderedAt(LocalDateTime.ofInstant(NOW, ZoneId.of("Europe/Warsaw")).minusDays(1));
        return order;
    }
}
