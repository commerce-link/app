package pl.commercelink.web.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.search.ProductHeader;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.products.PimCategoryTree;
import pl.commercelink.taxonomy.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCategoryLineFactoryTest {

    @Mock private TaxonomyCache taxonomyCache;
    @Mock private PimCatalog pimCatalog;
    private ProductCategoryLineFactory factory;

    @BeforeEach
    void setUp() {
        factory = new ProductCategoryLineFactory(taxonomyCache, new PimCategoryTree(pimCatalog));
    }

    @Test
    void foundProductGetsItsPimPathWithoutAnyCatalogState() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("10", null, "Komponenty komputerowe", "pl"), new PimCategory("11", "10", "Karty graficzne", "pl")));
        when(taxonomyCache.find(any())).thenReturn(new Taxonomy("5901000000001", "GPU-1", "Gigabyte", "RTX 4060", "Karty", 1,
                null, null, null, "11"));

        // when
        Optional<CategoryLine> line = factory.build(new ProductHeader("RTX 4060", "Gigabyte", "5901000000001", "GPU-1"));

        // then
        assertThat(line).isPresent();
        assertThat(line.get().pimAncestors()).containsExactly("Komponenty komputerowe");
        assertThat(line.get().pimLeaf()).isEqualTo("Karty graficzne");
        assertThat(line.get().inCatalog()).isFalse();
        assertThat(line.get().inCatalogLabels()).isEmpty();
    }

    @Test
    void productWithoutTaxonomyHasNoCategoryBlock() {
        // given
        when(taxonomyCache.find(any())).thenReturn(Taxonomy.EMPTY);

        // when / then
        assertThat(factory.build(new ProductHeader("X", "Y", "5901000000009", "X-9"))).isEmpty();
    }
}
