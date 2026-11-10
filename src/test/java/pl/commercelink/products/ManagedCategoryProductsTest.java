package pl.commercelink.products;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.InventoryKey;
import pl.commercelink.products.information.StorePimAliases;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagedCategoryProductsTest {

    private static final String STORE_ID = "store-1";
    private static final String CATEGORY_ID = "category-1";

    @Mock private ProductRepository productRepository;
    @Mock private CatalogPlacement catalogPlacement;
    @Mock private StorePimAliases pimAliases;

    @InjectMocks
    private ManagedCategoryProducts managedCategoryProducts;

    @Test
    void skipsACandidateWhoseEanOrPimIdTheCategoryHas() {
        // given
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(product("pim-1", "5900000000001")));
        ManagedCategoryProducts.Candidate sameEan = candidate(null, InventoryKey.fromEan("5900000000001"));
        ManagedCategoryProducts.Candidate samePimId = candidate(null, InventoryKey.fromPimIdAndEan("pim-1", "5900000000002"));
        ManagedCategoryProducts.Candidate missing = candidate("pim-3", InventoryKey.fromEan("5900000000003"));
        when(pimAliases.aliasesOf(STORE_ID, "pim-3")).thenReturn(Set.of("pim-3"));

        // when
        ManagedCategoryProducts.Added added = managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID,
                List.of(sameEan, samePimId, missing));

        // then
        assertThat(added).isEqualTo(new ManagedCategoryProducts.Added(1, 2));
        verify(productRepository).save(missing.product());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void skipsACandidateWhoseCloneOrSourceTheCategoryHas() {
        // given: the category holds the store's clone, the candidate comes with the global source
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(product("clone-1", "5900000000001")));
        ManagedCategoryProducts.Candidate source = candidate("source-1", InventoryKey.fromEan("5900000000009"));
        when(pimAliases.aliasesOf(STORE_ID, "source-1")).thenReturn(Set.of("source-1", "clone-1"));

        // when
        ManagedCategoryProducts.Added added = managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID, List.of(source));

        // then
        assertThat(added).isEqualTo(new ManagedCategoryProducts.Added(0, 1));
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void addsOneProductForTwoCandidatesWithOnePimId() {
        // given: one global entry split into two price groups, so two rows of one pim id
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of());
        ManagedCategoryProducts.Candidate cheaper = candidate("pim-1", InventoryKey.fromEan("5900000000001"));
        ManagedCategoryProducts.Candidate dearer = candidate("pim-1", InventoryKey.fromEan("5900000000002"));
        when(pimAliases.aliasesOf(STORE_ID, "pim-1")).thenReturn(Set.of("pim-1"));

        // when
        ManagedCategoryProducts.Added added = managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID, List.of(cheaper, dearer));

        // then
        assertThat(added).isEqualTo(new ManagedCategoryProducts.Added(1, 1));
        verify(productRepository).save(cheaper.product());
        verify(productRepository, never()).save(dearer.product());
    }

    @Test
    void countsACandidateAParallelSaveStoredFirstAsAlreadyInCategoryAndGuardsTheNextOnes() {
        // given
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of());
        ManagedCategoryProducts.Candidate storedMeanwhile = candidate("pim-1", InventoryKey.fromEan("5900000000001"));
        ManagedCategoryProducts.Candidate sameEan = candidate(null, InventoryKey.fromEan("5900000000001"));
        ManagedCategoryProducts.Candidate samePimId = candidate("pim-1", InventoryKey.fromEan("5900000000002"));
        when(pimAliases.aliasesOf(STORE_ID, "pim-1")).thenReturn(Set.of("pim-1"));
        doThrow(new ConditionalCheckFailedException("stored by the other request")).when(productRepository)
                .save(storedMeanwhile.product());

        // when
        ManagedCategoryProducts.Added added = managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID,
                List.of(storedMeanwhile, sameEan, samePimId));

        // then
        assertThat(added).isEqualTo(new ManagedCategoryProducts.Added(0, 3));
        verify(productRepository, times(1)).save(any(Product.class));
        verify(catalogPlacement, never()).evict(any());
    }

    @Test
    void evictsTheCatalogPlacementWhenSomethingWasAdded() {
        // given
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of());

        // when
        managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID, List.of(candidate(null, InventoryKey.fromEan("5900000000001"))));

        // then
        verify(catalogPlacement).evict(STORE_ID);
    }

    @Test
    void leavesTheCatalogPlacementWhenNothingWasAdded() {
        // given
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(product(null, "5900000000001")));

        // when
        ManagedCategoryProducts.Added added = managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID,
                List.of(candidate(null, InventoryKey.fromEan("5900000000001"))));

        // then
        assertThat(added).isEqualTo(new ManagedCategoryProducts.Added(0, 1));
        verify(catalogPlacement, never()).evict(any());
    }

    @Test
    void completesOnlyACandidateTheCategoryDoesNotHoldByItsKeyAndComparesThePimIdItGotThen() {
        // given: the PIM entry of a reviewed row is joined only once the row is not in the category by its EAN
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of(product(null, "5900000000001")));
        ManagedCategoryProducts.Candidate held = candidate(null, InventoryKey.fromEan("5900000000001"));
        ManagedCategoryProducts.Candidate first = candidate(null, InventoryKey.fromEan("5900000000002"));
        ManagedCategoryProducts.Candidate sameEntry = candidate(null, InventoryKey.fromEan("5900000000003"));
        when(pimAliases.aliasesOf(STORE_ID, "pim-joined")).thenReturn(Set.of("pim-joined"));
        List<Product> completed = new ArrayList<>();

        // when
        ManagedCategoryProducts.Added added = managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID,
                List.of(held, first, sameEntry), product -> {
                    completed.add(product);
                    product.setPimId("pim-joined");
                });

        // then
        assertThat(completed).containsExactly(first.product(), sameEntry.product());
        assertThat(added).isEqualTo(new ManagedCategoryProducts.Added(1, 2));
        verify(productRepository).save(first.product());
        verify(productRepository, times(1)).save(any(Product.class));
    }

    @Test
    void anotherFailureOfASaveReachesTheCaller() {
        // given
        when(productRepository.findAll(CATEGORY_ID)).thenReturn(List.of());
        ManagedCategoryProducts.Candidate candidate = candidate(null, InventoryKey.fromEan("5900000000001"));
        doThrow(new IllegalStateException("table unavailable")).when(productRepository).save(candidate.product());

        // when / then
        assertThatThrownBy(() -> managedCategoryProducts.addMissing(STORE_ID, CATEGORY_ID, List.of(candidate)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Product product(String pimId, String ean) {
        return new Product(CATEGORY_ID, pimId, ean, null, "Brand", "Label", "Name", "Default");
    }

    private static ManagedCategoryProducts.Candidate candidate(String pimId, InventoryKey key) {
        String ean = key.getProductEans().iterator().next();
        return new ManagedCategoryProducts.Candidate(product(pimId, ean), key);
    }
}
