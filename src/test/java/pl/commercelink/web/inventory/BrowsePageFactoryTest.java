package pl.commercelink.web.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.BrowseCriteria;
import pl.commercelink.inventory.BrowseResult;
import pl.commercelink.inventory.BrowseRow;
import pl.commercelink.inventory.BrowseSummary;
import pl.commercelink.inventory.InventoryBrowse;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.CatalogPlacement;
import pl.commercelink.products.PimCategoryTree;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BrowsePageFactoryTest {

    private static final String STORE_ID = "store-1";

    @Mock private InventoryBrowse inventoryBrowse;
    @Mock private CatalogPlacement catalogPlacement;
    private BrowsePageFactory factory;

    @BeforeEach
    void setUp() {
        PimCatalog pimCatalog = mock(PimCatalog.class);
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty komputerowe", "pl"),
                new PimCategory("11", "10", "Karty graficzne", "pl"),
                new PimCategory("12", "10", "Dyski SSD", "pl"),
                new PimCategory("20", null, "Akcesoria", "pl"),
                new PimCategory("21", "20", "Kable", "pl")));
        factory = new BrowsePageFactory(inventoryBrowse, new PimCategoryTree(pimCatalog), catalogPlacement);
        when(inventoryBrowse.summary(any())).thenReturn(new BrowseSummary(Map.of("11", 3, "12", 2, "21", 9), Map.of("AB", 14), 14));
        when(inventoryBrowse.browse(any(), any())).thenReturn(new BrowseResult(List.of(), 0, false));
        CatalogPlacement.Target gpu = new CatalogPlacement.Target("c-1", "Podzespoły", "cat-gpu", "Karta graficzna", List.of("11"));
        CatalogPlacement.Target b2b = new CatalogPlacement.Target("c-2", "Sklep B2B", "cat-b2b", "Karty", List.of("11"));
        CatalogPlacement.Existing existing = new CatalogPlacement.Existing("c-1", "cat-gpu", "p-1", InventoryKey.fromEan("5901000000001"));
        when(catalogPlacement.forStore(STORE_ID)).thenReturn(new CatalogPlacement.StorePlacement(List.of(gpu, b2b), List.of(existing)));
    }

    @Test
    void startShowsTopLevelTilesByCountAndQueriesNoRows() {
        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start(), true, false);

        // then
        assertThat(page.isStart()).isTrue();
        assertThat(page.tiles()).extracting(BrowsePage.Tile::label).containsExactly("Akcesoria", "Komponenty komputerowe");
        assertThat(page.tiles()).extracting(BrowsePage.Tile::count).containsExactly(9, 5);
        assertThat(page.tiles().get(1).description()).isEqualTo("Karty graficzne, Dyski SSD");
        verify(inventoryBrowse, never()).browse(any(), any());
    }

    @Test
    void categoryShowsCrumbsChildrenAndQueriesItsWholeSubtree() {
        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("10"), true, false);

        // then
        ArgumentCaptor<BrowseCriteria> criteria = ArgumentCaptor.forClass(BrowseCriteria.class);
        verify(inventoryBrowse).browse(eq(STORE_ID), criteria.capture());
        assertThat(criteria.getValue().categoryIds()).containsExactlyInAnyOrder("10", "11", "12");
        assertThat(page.title()).isEqualTo("Komponenty komputerowe");
        assertThat(page.crumbs()).extracting(BrowsePage.Crumb::label).containsExactly(null, "Komponenty komputerowe");
        assertThat(page.subnavSiblings()).isFalse();
        assertThat(page.subnav()).extracting(BrowsePage.NavItem::label).containsExactly("Dyski SSD", "Karty graficzne");
    }

    @Test
    void leafShowsSiblingsWithTheCurrentOneMarked() {
        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), true, false);

        // then
        assertThat(page.subnavSiblings()).isTrue();
        assertThat(page.subnav()).filteredOn(BrowsePage.NavItem::current).extracting(BrowsePage.NavItem::label)
                .containsExactly("Karty graficzne");
    }

    @Test
    void rowShowsPimPathCatalogLineAndInCatalogLinkForAdmin() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                row("5901000000001", "11", "Karty graficzne")), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("10"), true, false);

        // then
        BrowsePage.RowView row = page.rows().get(0);
        assertThat(row.category().pimAncestors()).containsExactly("Komponenty komputerowe");
        assertThat(row.category().pimLeaf()).isEqualTo("Karty graficzne");
        assertThat(row.category().catalogLabel()).isEqualTo("Podzespoły › Karta graficzna");
        assertThat(row.category().catalogMore()).isEqualTo(1);
        assertThat(row.category().inCatalogHref()).isEqualTo("/dashboard/catalogs/c-1/category/cat-gpu/products/p-1");
        assertThat(row.detailHref()).startsWith("/dashboard/inventory?q=5901000000001&from=");
    }

    @Test
    void rowWithUnknownCategoryIdFallsBackToCategoryText() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                row("5901000000002", "777", "Zasilacze awaryjne")), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("777"), true, false);

        // then
        assertThat(page.rows().get(0).category().pimAncestors()).isEmpty();
        assertThat(page.rows().get(0).category().pimLeaf()).isEqualTo("Zasilacze awaryjne");
        assertThat(page.rows().get(0).category().catalogUnmatched()).isTrue();
    }

    @Test
    void nonAdminGetsNoCatalogLineAndNoPlacementLookup() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenReturn(new BrowseResult(List.of(
                row("5901000000001", "11", "Karty graficzne")), 1, false));

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11"), false, false);

        // then
        assertThat(page.rows().get(0).category().catalogLabel()).isNull();
        assertThat(page.rows().get(0).category().catalogUnmatched()).isFalse();
        verifyNoInteractions(catalogPlacement);
    }

    @Test
    void catalogFilterOutKeepsOnlyProductsOutsideTheCatalogs() {
        // when
        factory.build(STORE_ID, BrowseQuery.start().withCategory("11").withCatalog(BrowseQuery.CatalogFilter.OUT), true, false);

        // then
        ArgumentCaptor<BrowseCriteria> criteria = ArgumentCaptor.forClass(BrowseCriteria.class);
        verify(inventoryBrowse).browse(eq(STORE_ID), criteria.capture());
        assertThat(criteria.getValue().rowFilter().test(row("5901000000001", "11", "x"))).isFalse();
        assertThat(criteria.getValue().rowFilter().test(row("5909999999999", "11", "x"))).isTrue();
    }

    @Test
    void pageIsClampedToLastPage() {
        // given
        when(inventoryBrowse.browse(eq(STORE_ID), any())).thenAnswer(call -> {
            BrowseCriteria criteria = call.getArgument(1);
            return criteria.offset() >= 120
                    ? new BrowseResult(List.of(), 120, false)
                    : new BrowseResult(List.of(row("5901000000001", "11", "x")), 120, false);
        });

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start().withCategory("11").withPage(9), true, false);

        // then
        assertThat(page.pagination().page()).isEqualTo(3);
        assertThat(page.rows()).hasSize(1);
    }

    @Test
    void pageWithoutSuppliersFlagsNoSuppliers() {
        // given
        when(inventoryBrowse.summary(STORE_ID)).thenReturn(BrowseSummary.EMPTY);

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.start(), true, false);

        // then
        assertThat(page.noSuppliers()).isTrue();
    }

    @Test
    void textTooShortSkipsTheQuery() {
        // given
        org.springframework.util.LinkedMultiValueMap<String, String> params = new org.springframework.util.LinkedMultiValueMap<>();
        params.add("q2", "ab");

        // when
        BrowsePage page = factory.build(STORE_ID, BrowseQuery.parse(params), true, false);

        // then
        assertThat(page.textTooShort()).isTrue();
        verify(inventoryBrowse, never()).browse(anyString(), any());
    }

    private static BrowseRow row(String ean, String categoryId, String categoryText) {
        return new BrowseRow(InventoryKey.fromEan(ean), "Produkt " + ean, "Brand", ean, "MFN-" + ean, categoryId,
                categoryText, 120.0, true, "AB", 5, 2);
    }
}
