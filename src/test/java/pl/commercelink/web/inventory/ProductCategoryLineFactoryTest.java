package pl.commercelink.web.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.pim.ScopedPimCatalog;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.products.ProductDataResolver;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryLineFactoryTest {

    private static final String STORE_ID = "store-1";
    private static final String EAN = "5901000000001";
    private static final ProductHeader HEADER = new ProductHeader("RTX 4060", "Gigabyte", EAN, "GPU-1");

    @Mock private PimCatalog categories;
    private final TaxonomyCache taxonomy = new TaxonomyCache(mock(TaxonomyRepository.class));

    private ProductCategoryLineFactory factory(PimEntry... pimEntries) {
        return new ProductCategoryLineFactory(new ProductDataResolver(ScopedPimCatalog.of(pimEntries), taxonomy),
                new PimCategoryTree(categories));
    }

    private void categoryTree() {
        when(categories.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty komputerowe", "pl"),
                new PimCategory("11", "10", "Karty graficzne", "pl"),
                new PimCategory("12", "10", "Procesory", "pl")));
    }

    @Test
    void foundProductGetsItsPimPathWithoutAnyCatalogState() {
        // given
        categoryTree();
        taxonomy.add(new Taxonomy(EAN, "GPU-1", "Gigabyte", "RTX 4060", "Karty", 1, null, null, null, "11"));

        // when
        Optional<CategoryLine> line = factory().build(STORE_ID, HEADER);

        // then
        assertThat(line).isPresent();
        assertThat(line.get().pimAncestors()).containsExactly("Komponenty komputerowe");
        assertThat(line.get().pimLeaf()).isEqualTo("Karty graficzne");
        assertThat(line.get().inCatalog()).isFalse();
        assertThat(line.get().inCatalogLabels()).isEmpty();
    }

    @Test
    void storesPimEntryGivesTheCategoryBeforeTheTaxonomy() {
        // given
        categoryTree();
        taxonomy.add(new Taxonomy(EAN, "GPU-1", "Gigabyte", "RTX 4060", "Karty", 1, null, null, null, "11"));
        ProductCategoryLineFactory factory = factory(new PimEntry("s-1", ScopedPimCatalog.identifiers(EAN, null),
                "Gigabyte", "RTX 4060", "CPU", null, true, null, null, "12", STORE_ID));

        // when
        Optional<CategoryLine> line = factory.build(STORE_ID, HEADER);

        // then
        assertThat(line).map(CategoryLine::pimLeaf).contains("Procesory");
    }

    @Test
    void productNobodyKnowsHasNoCategoryBlock() {
        // when / then
        assertThat(factory().build(STORE_ID, new ProductHeader("X", "Y", "5901000000009", "X-9"))).isEmpty();
    }
}
