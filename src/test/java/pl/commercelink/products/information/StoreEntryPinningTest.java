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

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
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
    private final StoreEntryCodeHistory history = new StoreEntryCodeHistory(Clock.systemUTC());
    private StoreEntryPinning pinning;

    @BeforeEach
    void setUp() {
        when(productCatalogRepository.findAll(STORE)).thenReturn(List.of(catalogWith(STORE_CATEGORY)));
        when(productCatalogRepository.findAll(OTHER_STORE)).thenReturn(List.of(catalogWith(OTHER_STORE_CATEGORY)));
        when(productCatalogRepository.findAllOfEveryStore()).thenReturn(List.of(
                catalogOf(STORE, STORE_CATEGORY), catalogOf(OTHER_STORE, OTHER_STORE_CATEGORY)));
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
        pinning = new StoreEntryPinning(productRepository, productCatalogRepository, history);
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
    void productPinnedByACodeOnlyTheHistoryStillKnowsIsDetached() {
        // given: another event's refresh already put the new codes into the copy
        history.remember(List.of(storeEntry("X", STORE, List.of(E1), List.of())));
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E2), List.of()));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isNull();
    }

    @Test
    void productPinnedBySiblingEanStaysWhenTheHistoryKnowsTheOldCodes() {
        // given
        history.remember(List.of(storeEntry("X", STORE, List.of(E1), List.of())));
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E2), List.of()));
        Product product = product("p1", STORE_CATEGORY, "X", E9, "M9");

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void codeTheEditPutBackIsNotDetachedThoughTheHistoryHasOthers() {
        // given: E1 -> E2 -> back to E1
        history.remember(List.of(storeEntry("X", STORE, List.of(E1), List.of())));
        history.remember(List.of(storeEntry("X", STORE, List.of(E2), List.of())));
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        pinning.onStoreEntryAnnounced(event("X", List.of(E1), List.of()), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("X");
    }

    @Test
    void everyPinChangeIsLoggedWithTheProductAndTheReason() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));
        product("p1", STORE_CATEGORY, "X", E1, null);
        product("p2", STORE_CATEGORY, null, E2, null);

        // when
        List<String> infos;
        try (CapturedLogs logs = CapturedLogs.of(StoreEntryPinning.class)) {
            pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);
            infos = logs.infos();
        }

        // then
        assertThat(infos).anySatisfy(line -> assertThat(line)
                .contains("c1/p1").contains("X -> null").contains("removed"));
        assertThat(infos).anySatisfy(line -> assertThat(line)
                .contains("c1/p2").contains("null -> X").contains("matches"));
        assertThat(infos).anySatisfy(line -> assertThat(line).contains("1 products unpinned, 1 pinned"));
    }

    @Test
    void failedSaveLeavesNoPinChangeInTheLog() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));
        product("p1", STORE_CATEGORY, "X", E1, null);
        doThrow(new IllegalStateException("save failed")).when(productRepository).save(any());

        // when
        List<String> infos;
        try (CapturedLogs logs = CapturedLogs.of(StoreEntryPinning.class)) {
            assertThatThrownBy(() -> pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy))
                    .isInstanceOf(IllegalStateException.class);
            infos = logs.infos();
        }

        // then
        assertThat(infos).noneSatisfy(line -> assertThat(line).contains("c1/p1").contains(" pin "));
    }

    @Test
    void deletedEntryIsLoggedAsTheOldPinOfARepinnedProduct() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                storeEntry("X", STORE, List.of(E1), List.of()),
                ScopedPimCatalog.globalEntry("G", E1, null, "cat"));
        product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        List<String> infos;
        try (CapturedLogs logs = CapturedLogs.of(StoreEntryPinning.class)) {
            pinning.onEntryDeleted("X", copy);
            infos = logs.infos();
        }

        // then
        assertThat(infos).anySatisfy(line -> assertThat(line).contains("c1/p1").contains("pin X -> G"));
    }

    @Test
    void eventThatChangesNothingIsLoggedAndSaysTheOldCodesAreUnknown() {
        // given
        PimCatalog copy = ScopedPimCatalog.of();
        Product product = product("p1", STORE_CATEGORY, "X", E1, null);

        // when
        List<String> infos;
        try (CapturedLogs logs = CapturedLogs.of(StoreEntryPinning.class)) {
            pinning.onStoreEntryAnnounced(event("X", List.of(E2), List.of()), copy);
            infos = logs.infos();
        }

        // then
        assertThat(product.getPimId()).isEqualTo("X");
        assertThat(infos).singleElement().satisfies(line -> assertThat(line)
                .contains("PIM entry X of store s1").contains("0 products unpinned, 0 pinned")
                .contains("unknown to this instance"));
    }

    @Test
    void eventWithKnownOldCodesDoesNotSayTheyAreUnknown() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(storeEntry("X", STORE, List.of(E1), List.of()));

        // when
        List<String> infos;
        try (CapturedLogs logs = CapturedLogs.of(StoreEntryPinning.class)) {
            pinning.onStoreEntryAnnounced(event("X", List.of(E1), List.of()), copy);
            infos = logs.infos();
        }

        // then
        assertThat(infos).singleElement().satisfies(line -> assertThat(line)
                .contains("0 products unpinned, 0 pinned").doesNotContain("unknown"));
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

    @Test
    void ownersProductsOnTheSourceMoveToTheClone() {
        // given: the product is pinned to the source by a sibling code the clone does not have
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", STORE, "S", E1, null, "cat"));
        Product product = product("p1", STORE_CATEGORY, "S", E9, null);

        // when
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

        // then
        assertThat(product.getPimId()).isEqualTo("C");
        var order = inOrder(copy, productRepository);
        order.verify(copy).refresh();
        order.verify(productRepository).save(product);
    }

    @Test
    void otherStoresProductsOnTheSourceStay() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", STORE, "S", E1, null, "cat"));
        Product elsewhere = product("p1", OTHER_STORE_CATEGORY, "S", E1, null);
        Product unpinnedElsewhere = product("p2", OTHER_STORE_CATEGORY, null, E1, null);

        // when
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

        // then
        assertThat(elsewhere.getPimId()).isEqualTo("S");
        assertThat(unpinnedElsewhere.getPimId()).isNull();
    }

    @Test
    void unpinnedMatchingProductIsPinnedToTheClone() {
        // given: the clone's codes in the refreshed copy count, not the ones the delayed event carries
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", STORE, "S", E2, null, "cat"),
                storeEntry("X", STORE, List.of(E3), List.of()));
        Product unpinned = product("p1", STORE_CATEGORY, null, E2, null);
        Product onSibling = product("p2", STORE_CATEGORY, "X", E2, null);
        Product byStaleCode = product("p3", STORE_CATEGORY, null, E1, null);

        // when
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

        // then
        assertThat(unpinned.getPimId()).isEqualTo("C");
        assertThat(onSibling.getPimId()).isEqualTo("C");
        assertThat(byStaleCode.getPimId()).isNull();
    }

    @Test
    void repeatedCloneEventChangesNothing() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", STORE, "S", E1, null, "cat"));
        Product onSource = product("p1", STORE_CATEGORY, "S", E1, null);
        Product unpinned = product("p2", STORE_CATEGORY, null, E1, null);
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);
        clearInvocations(productRepository);

        // when
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

        // then
        assertThat(onSource.getPimId()).isEqualTo("C");
        assertThat(unpinned.getPimId()).isEqualTo("C");
        verify(productRepository, never()).save(any());
    }

    @Test
    void cloneDeletedBeforeTheEventMovesNothing() {
        // given: the refreshed copy no longer has the clone
        PimCatalog copy = ScopedPimCatalog.of(ScopedPimCatalog.globalEntry("S", E1, null, "cat"));
        Product onSource = product("p1", STORE_CATEGORY, "S", E1, null);
        Product unpinned = product("p2", STORE_CATEGORY, null, E1, null);

        // when
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

        // then
        verify(copy).refresh();
        assertThat(onSource.getPimId()).isEqualTo("S");
        assertThat(unpinned.getPimId()).isNull();
        verify(productRepository, never()).save(any());
    }

    @Test
    void cloneMissingFromTheRefreshedCopyIsLoggedAsAWarning() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(ScopedPimCatalog.globalEntry("S", E1, null, "cat"));
        product("p1", STORE_CATEGORY, "S", E1, null);

        // when
        try (CapturedLogs logs = CapturedLogs.of(StoreEntryPinning.class)) {
            pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

            // then
            assertThat(logs.warnings()).singleElement().asString()
                    .contains("C", "S", STORE, "not in the PIM index copy after the refresh attempt",
                            "resend pim-entry-added manually after the next PIM index rebuild", "runbook")
                    .doesNotContain("refreshed PIM index");
            assertThat(logs.infos()).isEmpty();
        }
    }

    @Test
    void cloneEventWithAnotherSourceMovesNothing() {
        // given: the refreshed clone was cloned from S2, the event names S
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.globalEntry("S2", E2, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", STORE, "S2", E1, null, "cat"));
        Product onSource = product("p1", STORE_CATEGORY, "S", E9, null);
        Product unpinned = product("p2", STORE_CATEGORY, null, E1, null);

        // when
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

        // then
        assertThat(onSource.getPimId()).isEqualTo("S");
        assertThat(unpinned.getPimId()).isNull();
        verify(productRepository, never()).save(any());
    }

    @Test
    void cloneOwnedByAnotherStoreThanTheEventsMovesNothing() {
        // given: C belongs to the other store, the event names this store as the owner
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", OTHER_STORE, "S", E1, null, "cat"));
        Product onSource = product("p1", STORE_CATEGORY, "S", E9, null);
        Product unpinned = product("p2", STORE_CATEGORY, null, E1, null);
        Product otherStoresOnSource = product("p3", OTHER_STORE_CATEGORY, "S", E1, null);

        // when
        pinning.onCloneAnnounced(cloneEvent("C", "S", List.of(E1)), copy);

        // then
        assertThat(onSource.getPimId()).isEqualTo("S");
        assertThat(unpinned.getPimId()).isNull();
        assertThat(otherStoresOnSource.getPimId()).isEqualTo("S");
        verify(productRepository, never()).save(any());
    }

    @Test
    void deletingTheCloneReturnsProductsToTheSource() {
        // given: the copy is not refreshed yet and still holds the clone
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", STORE, "S", E1, null, "cat"));
        Product product = product("p1", STORE_CATEGORY, "C", E1, null);

        // when
        pinning.onEntryDeleted("C", copy);

        // then
        assertThat(product.getPimId()).isEqualTo("S");
    }

    @Test
    void productPinnedToTheCloneBySiblingCodeReturnsToTheSource() {
        // given: no entry has the product's own code, so the code rule alone would leave it unpinned
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.cloneEntry("C", STORE, "S", E1, null, "cat"));
        Product product = product("p1", STORE_CATEGORY, "C", E9, null);

        // when
        pinning.onEntryDeleted("C", copy);

        // then
        assertThat(product.getPimId()).isEqualTo("S");
    }

    @Test
    void productPinnedToTheCloneByItsEditedCodesDoesNotGoToTheSource() {
        // given: the store changed the clone's EAN from E1 to E2, which pinned an unrelated product to it; the copy
        // is not refreshed yet when the clone is deleted
        Product unrelated = product("p1", STORE_CATEGORY, "C", E2, null);
        Product unrelatedWithoutGlobalMatch = product("p2", STORE_CATEGORY, "C", null, "MFN-ONLY-ON-CLONE");
        Product fromTheSource = product("p3", STORE_CATEGORY, "C", E1, null);
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                ScopedPimCatalog.globalEntry("G", E2, null, "cat"),
                cloneWithCodes("C", STORE, "S", List.of(E2), List.of("MFN-ONLY-ON-CLONE")));

        // when
        pinning.onEntryDeleted("C", copy);

        // then: the products the clone's own codes attached follow the code rule; the source's product goes back
        assertThat(unrelated.getPimId()).isEqualTo("G");
        assertThat(unrelatedWithoutGlobalMatch.getPimId()).isNull();
        assertThat(fromTheSource.getPimId()).isEqualTo("S");
    }

    @Test
    void productMatchingBothTheCloneAndTheSourceReturnsToTheSource() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.globalEntry("S", E1, null, "cat"),
                cloneWithCodes("C", STORE, "S", List.of(E1, E2), List.of()));
        Product product = product("p1", STORE_CATEGORY, "C", E1, null);

        // when
        pinning.onEntryDeleted("C", copy);

        // then
        assertThat(product.getPimId()).isEqualTo("S");
    }

    @Test
    void deletingTheCloneWhenTheSourceIsGoneFallsBackToCodes() {
        // given
        PimCatalog copy = ScopedPimCatalog.of(
                ScopedPimCatalog.cloneEntry("C", STORE, "S", E1, null, "cat"),
                ScopedPimCatalog.globalEntry("G", E1, null, "cat"));
        Product matching = product("p1", STORE_CATEGORY, "C", E1, null);
        Product bySibling = product("p2", STORE_CATEGORY, "C", E9, null);

        // when
        pinning.onEntryDeleted("C", copy);

        // then
        assertThat(matching.getPimId()).isEqualTo("G");
        assertThat(bySibling.getPimId()).isNull();
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

    private static PimEntry cloneWithCodes(String pimId, String storeId, String sourcePimId, List<String> gtins,
                                           List<String> mpns) {
        List<PimIdentifier> identifiers = Stream.concat(
                gtins.stream().map(gtin -> new PimIdentifier(gtin, PimIdentifierType.GTIN)),
                mpns.stream().map(mpn -> new PimIdentifier(mpn, PimIdentifierType.MPN))).toList();
        return new PimEntry(pimId, identifiers, "Brand", "Clone " + pimId, "cat", null, true, 2000, 2200, null, storeId,
                sourcePimId);
    }

    private static PIMEntryAddedEvent cloneEvent(String pimId, String sourcePimId, List<String> eans) {
        return new PIMEntryAddedEvent(pimId, eans, List.of(), STORE, sourcePimId);
    }

    private static ProductCatalog catalogOf(String storeId, String categoryId) {
        ProductCatalog catalog = catalogWith(categoryId);
        catalog.setStoreId(storeId);
        return catalog;
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
