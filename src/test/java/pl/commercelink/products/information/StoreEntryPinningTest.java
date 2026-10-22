package pl.commercelink.products.information;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PIMEntryAddedEvent;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.CategoryDefinition;
import pl.commercelink.products.Product;
import pl.commercelink.products.ProductCatalog;
import pl.commercelink.products.ProductCatalogRepository;
import pl.commercelink.products.ProductRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StoreEntryPinningTest {

    private static final String STORE = "s1";
    private static final String OTHER_STORE = "s2";
    private static final String STORE_CATEGORY = "c1";
    private static final String OTHER_STORE_CATEGORY = "c2";

    private static final String E1 = "5900000000011";
    private static final String E2 = "5900000000022";
    private static final String E3 = "5900000000033";
    private static final String E9 = "5900000000099";

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductCatalogRepository productCatalogRepository;

    private final List<Product> products = new ArrayList<>();
    private StoreEntryPinning pinning;

    @BeforeEach
    void setUp() {
        when(productCatalogRepository.findAll(STORE)).thenReturn(List.of(catalogWith(STORE_CATEGORY)));
        when(productCatalogRepository.findAll(OTHER_STORE)).thenReturn(List.of(catalogWith(OTHER_STORE_CATEGORY)));
        // an in-memory product table: queries see the pins written by earlier events
        when(productRepository.findAllByPimId(anyString())).thenAnswer(invocation -> products.stream()
                .filter(product -> Objects.equals(product.getPimId(), invocation.getArgument(0)))
                .map(StoreEntryPinningTest::indexProjection)
                .toList());
        when(productRepository.findByProductId(anyString(), anyString())).thenAnswer(invocation -> products.stream()
                .filter(product -> product.getCategoryId().equals(invocation.getArgument(0))
                        && product.getProductId().equals(invocation.getArgument(1)))
                .findFirst().orElse(null));
        when(productRepository.findAllByEanOrMfnInCategories(any(), any(), any())).thenAnswer(invocation -> {
            Collection<String> categoryIds = invocation.getArgument(0);
            List<String> eans = invocation.getArgument(1);
            List<String> mfnCodes = invocation.getArgument(2);
            return products.stream()
                    .filter(product -> categoryIds.contains(product.getCategoryId()))
                    .filter(product -> (product.getEan() != null && eans.contains(product.getEan()))
                            || (product.getManufacturerCode() != null && mfnCodes.contains(product.getManufacturerCode())))
                    .toList();
        });
        when(productRepository.detachPimFromProducts(anyString())).thenAnswer(invocation -> {
            List<Product> detached = products.stream()
                    .filter(product -> Objects.equals(product.getPimId(), invocation.getArgument(0)))
                    .toList();
            detached.forEach(product -> product.setPimId(null));
            return detached;
        });
        pinning = new StoreEntryPinning(productRepository, productCatalogRepository);
    }

    @Test
    void productPinnedByARemovedEanIsDetached() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isNull();
    }

    @Test
    void productPinnedBySiblingEanStays() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));
        Product product = product("p1", STORE_CATEGORY, "X", E9, "M9");

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void nothingIsDetachedWhenTheCopyAlreadyHasTheNewCodes() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E2), List.of()));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void nothingIsDetachedWhenTheCopyDoesNotKnowTheEntry() {
        // given
        PimCatalog copy = ScopedPimCatalog.of();
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void repeatedEventWithUnchangedCodesDetachesNothing() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of("M1")));
        Product product = product("p1", STORE_CATEGORY, "X", E1, "M1");

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E1), List.of("M1")), copy);
        pinning.onStoreEntryAnnounced(event("X", List.of(E1), List.of("M1")), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void productStillMatchingByMpnStays() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of("M1")));
        Product product = product("p1", STORE_CATEGORY, "X", E1, "M1");

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of("m 1")), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void detachedProductIsPinnedToAMatchingGlobalEntry() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                storeEntry("X", STORE, List.of(E1), List.of()),
                ScopedPimCatalog.globalEntry("G", E1, null, "cat"));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("G");
    }

    @Test
    void detachedProductIsNotPinnedBackToTheSameEntry() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(ScopedPimCatalog.globalEntry("X", E1, null, "cat"));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isNull();
    }

    @Test
    void unpinnedProductMatchingTheNewEanIsPinned() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));
        Product product = product("p1", STORE_CATEGORY, null, "0" + E2, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of("0" + E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void eanMovedBetweenStoreEntriesEndsOnTheNewEntryInBothOrders() {
        for (boolean aFirst : List.of(true, false)) {
            // given
            products.clear();
            PimCatalog copy = ScopedPimCatalog.of(
                    storeEntry("A", STORE, List.of(E1), List.of()),
                    storeEntry("B", STORE, List.of(E2), List.of()));
            Product product = product("p1", STORE_CATEGORY, "A", E1, null);
            PIMEntryAddedEvent aChanged = event("A", List.of(E3), List.of());
            PIMEntryAddedEvent bChanged = event("B", List.of(E2, E1), List.of());

            // when
            Stream.of(aFirst ? aChanged : bChanged, aFirst ? bChanged : aChanged)
                    .forEach(event -> pinning.onStoreEntryAnnounced(event, copy));

            // then
            assertThat(product.getPimId()).as("A first: %s", aFirst).isEqualTo("B");
        }
    }

    @Test
    void productPinnedToAGlobalEntryIsNotMovedByAStoreEntry() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("G", E1, null, "cat"),
                storeEntry("X", STORE, List.of(E2), List.of()));
        Product product = product("p1", STORE_CATEGORY, "G", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2, E1), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("G");
    }

    @Test
    void productsOfOtherStoresAreUntouched() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                storeEntry("X", STORE, List.of(E1), List.of()),
                storeEntry("Y", OTHER_STORE, List.of(E3), List.of()));
        Product pinnedElsewhere = product("p1", OTHER_STORE_CATEGORY, "X", E1, null);
        Product unpinnedElsewhere = product("p2", OTHER_STORE_CATEGORY, null, E2, null);
        Product onOtherStoreEntry = product("p3", OTHER_STORE_CATEGORY, "Y", E2, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(pinnedElsewhere.getPimId()).isEqualTo("X");
        assertThat(unpinnedElsewhere.getPimId()).isNull();
        assertThat(onOtherStoreEntry.getPimId()).isEqualTo("Y");
    }

    @Test
    void deleteDetachesAndPinsToAGlobalEntry() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                storeEntry("X", STORE, List.of(E1), List.of()),
                ScopedPimCatalog.globalEntry("G", E1, null, "cat"));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onEntryDeleted("X", copy);

        // then
        assertThat(product.getPimId()).isEqualTo("G");
    }

    @Test
    void deleteOfAGlobalEntryDoesNotPinBackToItself() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(ScopedPimCatalog.globalEntry("G", E1, null, "cat"));
        Product product = product("p1", STORE_CATEGORY, "G", E1, null);

        // when
        pinning.onEntryDeleted("G", copy);

        // then
        assertThat(product.getPimId()).isNull();
    }

    @Test
    void deleteWithoutAGlobalMatchLeavesTheProductPending() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onEntryDeleted("X", copy);

        // then
        assertThat(product.getPimId()).isNull();
    }

    private Product product(String productId, String categoryId, String pimId, String ean, String mfn) {
        Product product = new Product();
        product.setProductId(productId);
        product.setCategoryId(categoryId);
        product.setPimId(pimId);
        product.setEan(ean);
        product.setManufacturerCode(mfn);
        products.add(product);
        return product;
    }

    // PimIdIndex projects only the keys and productId
    private static Product indexProjection(Product product) {
        Product projection = new Product();
        projection.setCategoryId(product.getCategoryId());
        projection.setProductId(product.getProductId());
        projection.setPimId(product.getPimId());
        return projection;
    }

    private static PIMEntryAddedEvent event(String pimId, List<String> eans, List<String> mfnCodes) {
        return new PIMEntryAddedEvent(pimId, eans, mfnCodes, STORE);
    }

    private static PimEntry storeEntry(String pimId, String storeId, List<String> gtins, List<String> mpns) {
        List<PimIdentifier> identifiers = Stream.concat(
                gtins.stream().map(gtin -> new PimIdentifier(gtin, PimIdentifierType.GTIN)),
                mpns.stream().map(mpn -> new PimIdentifier(mpn, PimIdentifierType.MPN))).toList();
        return new PimEntry(pimId, identifiers, "Brand", "Store " + pimId, "cat", null, true, 2000, 2200, null, storeId);
    }

    private static ProductCatalog catalogWith(String... categoryIds) {
        ProductCatalog catalog = new ProductCatalog();
        for (String id : categoryIds) {
            CategoryDefinition category = new CategoryDefinition();
            category.setCategoryId(id);
            catalog.getCategories().add(category);
        }
        return catalog;
    }
}
