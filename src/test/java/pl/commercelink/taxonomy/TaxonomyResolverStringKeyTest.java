package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * F3 read-path retype: TaxonomyResolver.resolve keys on the String categoryKey.
 * Enum-era behavior is preserved (categoryKey == enum.name()); the additive win is that a
 * non-enum categoryKey now flows through the resolver instead of collapsing to Other.
 */
@ExtendWith(MockitoExtension.class)
class TaxonomyResolverStringKeyTest {

    @Mock
    private TaxonomyRepository taxonomyRepository;

    private TaxonomyCache seededCache(Taxonomy... taxonomies) {
        when(taxonomyRepository.loadNewest())
                .thenReturn(org.apache.commons.lang3.tuple.Pair.of("N/A", new java.util.ArrayList<>()));
        TaxonomyCache cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();
        for (Taxonomy taxonomy : taxonomies) {
            cache.add(taxonomy);
        }
        return cache;
    }

    @Test
    void resolvesNonEnumCategoryKeyAsString() {
        // given: enum is Other, but the taxonomy carries a non-enum key.
        TaxonomyCache cache = seededCache(
                new Taxonomy("E", "MFN1", "B", "Name", ProductCategory.Other, 5, null, null, "Cables356k"));
        TaxonomyResolver resolver = new TaxonomyResolver(cache);

        // when
        TaxonomyResolver.ResolvedProduct resolved = resolver.resolve("MFN1", "fallbackName", "Other");

        // then
        assertThat(resolved.categoryKey()).isEqualTo("Cables356k");
        assertThat(resolved.name()).isEqualTo("Name");
    }

    @Test
    void usesResolvedKeyWhenKnownAndNotOther() {
        // given
        TaxonomyCache cache = seededCache(new Taxonomy("E", "MFN1", "B", "Real Name", ProductCategory.Laptops, 5));
        TaxonomyResolver resolver = new TaxonomyResolver(cache);

        // when
        TaxonomyResolver.ResolvedProduct resolved = resolver.resolve("MFN1", "fallbackName", "Other");

        // then
        assertThat(resolved.categoryKey()).isEqualTo("Laptops");
    }

    @Test
    void keepsFallbackKeyWhenResolvedIsOther() {
        // given
        TaxonomyCache cache = seededCache(new Taxonomy("E", "MFN1", "B", "Name", ProductCategory.Other, 5));
        TaxonomyResolver resolver = new TaxonomyResolver(cache);

        // when
        TaxonomyResolver.ResolvedProduct resolved = resolver.resolve("MFN1", "fallbackName", "CPU");

        // then
        assertThat(resolved.categoryKey()).isEqualTo("CPU");
    }

    @Test
    void fallsBackEntirelyWhenMfnUnknown() {
        // given
        TaxonomyCache cache = seededCache();
        TaxonomyResolver resolver = new TaxonomyResolver(cache);

        // when
        TaxonomyResolver.ResolvedProduct resolved = resolver.resolve("UNKNOWN", "fb", "GPU");

        // then
        assertThat(resolved.name()).isEqualTo("fb");
        assertThat(resolved.categoryKey()).isEqualTo("GPU");
    }
}
