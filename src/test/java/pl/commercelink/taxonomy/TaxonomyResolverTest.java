package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.TaxonomyResolver.ResolvedProduct;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyResolverTest {

    @Mock
    private TaxonomyCache taxonomyCache;

    @InjectMocks
    private TaxonomyResolver taxonomyResolver;

    @Test
    void hitReturnsResolvedNameAndCategoryKey() {
        // given
        when(taxonomyCache.findByMfn("MFN-1")).thenReturn(taxonomy("Resolved Name", "CPU"));

        // when
        ResolvedProduct resolved = taxonomyResolver.resolve("MFN-1", "Fallback Name", "Laptops");

        // then
        assertThat(resolved.mfn()).isEqualTo("MFN-1");
        assertThat(resolved.name()).isEqualTo("Resolved Name");
        assertThat(resolved.category()).isEqualTo("CPU");
    }

    @Test
    void missFallsBackToProvidedNameAndCategoryKey() {
        // given
        when(taxonomyCache.findByMfn("MFN-1")).thenReturn(null);

        // when
        ResolvedProduct resolved = taxonomyResolver.resolve("MFN-1", "Fallback Name", "Laptops");

        // then
        assertThat(resolved.name()).isEqualTo("Fallback Name");
        assertThat(resolved.category()).isEqualTo("Laptops");
    }

    @Test
    void otherCategoryFallsBackToProvidedCategoryKey() {
        // given
        when(taxonomyCache.findByMfn("MFN-1")).thenReturn(taxonomy("Resolved Name", "Other"));

        // when
        ResolvedProduct resolved = taxonomyResolver.resolve("MFN-1", "Fallback Name", "Laptops");

        // then
        assertThat(resolved.name()).isEqualTo("Resolved Name");
        assertThat(resolved.category()).isEqualTo("Laptops");
    }

    @Test
    void nullCategoryWithNullFallbackYieldsNullCategory() {
        // given
        when(taxonomyCache.findByMfn("MFN-1")).thenReturn(taxonomy("Resolved Name", null));

        // when
        ResolvedProduct resolved = taxonomyResolver.resolve("MFN-1", "Fallback Name", null);

        // then
        assertThat(resolved.name()).isEqualTo("Resolved Name");
        assertThat(resolved.category()).isNull();
    }

    private static Taxonomy taxonomy(String name, String category) {
        return new Taxonomy("1234567890123", "MFN-1", "Brand", name, category, 1, null, null);
    }
}
