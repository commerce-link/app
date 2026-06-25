package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.marketplace.MarketplaceOrderImporter;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * SWEEP surface C — "Other as the no-information sentinel" in two resolution paths.
 *
 * Two distinct sites treat {@code ProductCategory.Other} as "unknown", but with OPPOSITE wiring:
 *
 *  - TaxonomyResolver.resolve(mfn, fallbackName, fallbackCategory): a resolved taxonomy category
 *    overrides the fallback ONLY when it is non-null AND != Other. Other coming FROM the taxonomy is
 *    treated as no info -> the caller's fallback wins.
 *
 *  - MarketplaceOrderImporter.resolveProductCategory(mfn): pimCatalog.findByMpn(mfn).map(category)
 *    .orElse(Other). Here Other is the DEFAULT when PIM has no entry, and PIM's category is taken
 *    VERBATIM (NOT approved-gated, NOT Other-filtered) — even an Other or a non-approved entry's
 *    category flows straight through. This contrasts with surface 13 (DataCorrection), where the
 *    category override IS approved-gated and Other-guarded.
 */
@ExtendWith(MockitoExtension.class)
class GoldenSweepOtherFallbackResolutionTest {

    @Mock
    private TaxonomyRepository taxonomyRepository;

    @Mock
    private PimCatalog pimCatalog;

    @InjectMocks
    private MarketplaceOrderImporter marketplaceOrderImporter;

    private static PimEntry pimEntry(ProductCategory category, boolean approved) {
        return new PimEntry("pid", List.of(), "brand", "name", category.name(), null, approved, null, null);
    }

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
    void taxonomyResolverUsesResolvedCategoryWhenKnownAndNotOther() {
        // given
        TaxonomyCache cache = seededCache(new Taxonomy("E", "MFN1", "B", "Real Name", ProductCategory.Laptops, 5));
        TaxonomyResolver resolver = new TaxonomyResolver(cache);

        // when
        TaxonomyResolver.ResolvedProduct resolved = resolver.resolve("MFN1", "fallbackName", ProductCategory.Other);

        // then
        // taxonomy category (Laptops) is non-null and != Other, so it overrides the Other fallback.
        assertThat(resolved.category()).isEqualTo(ProductCategory.Laptops);
        assertThat(resolved.name()).isEqualTo("Real Name");
    }

    @Test
    void taxonomyResolverKeepsFallbackWhenResolvedCategoryIsOther() {
        // given
        // the seeded taxonomy itself carries Other -> treated as "no category info".
        TaxonomyCache cache = seededCache(new Taxonomy("E", "MFN1", "B", "Name", ProductCategory.Other, 5));
        TaxonomyResolver resolver = new TaxonomyResolver(cache);

        // when
        TaxonomyResolver.ResolvedProduct resolved = resolver.resolve("MFN1", "fallbackName", ProductCategory.CPU);

        // then
        // Other from the taxonomy does NOT override; the caller's fallback (CPU) is kept.
        assertThat(resolved.category()).isEqualTo(ProductCategory.CPU);
    }

    @Test
    void taxonomyResolverFallsBackEntirelyWhenMfnUnknown() {
        // given
        TaxonomyCache cache = seededCache();
        TaxonomyResolver resolver = new TaxonomyResolver(cache);

        // when
        // findByMfn returns null for an unseeded mfn -> fallback name + fallback category verbatim.
        TaxonomyResolver.ResolvedProduct resolved = resolver.resolve("UNKNOWN", "fb", ProductCategory.GPU);

        // then
        assertThat(resolved.name()).isEqualTo("fb");
        assertThat(resolved.category()).isEqualTo(ProductCategory.GPU);
    }

    @Test
    void marketplaceResolveCategoryDefaultsToOtherWhenPimHasNoEntry() throws Exception {
        // given
        when(pimCatalog.findByMpn("MISSING")).thenReturn(Optional.empty());

        // when
        ProductCategory category = invokeResolveProductCategory("MISSING");

        // then
        // empty Optional -> Other default.
        assertThat(category).isEqualTo(ProductCategory.Other);
    }

    @Test
    void marketplaceResolveCategoryTakesPimCategoryVerbatimWithoutApprovedGate() throws Exception {
        // given
        // a NON-approved PIM entry whose category is Laptops.
        when(pimCatalog.findByMpn("MFN")).thenReturn(Optional.of(pimEntry(ProductCategory.Laptops, false)));

        // when
        ProductCategory category = invokeResolveProductCategory("MFN");

        // then
        // category flows through verbatim despite approved=false (NOT approved-gated, unlike DataCorrection).
        assertThat(category).isEqualTo(ProductCategory.Laptops);
    }

    @Test
    void marketplaceResolveCategoryPassesThroughAnOtherPimCategory() throws Exception {
        // given
        // PIM entry that itself carries Other -> taken verbatim (no Other-guard on this path).
        when(pimCatalog.findByMpn("MFN")).thenReturn(Optional.of(pimEntry(ProductCategory.Other, true)));

        // when
        ProductCategory category = invokeResolveProductCategory("MFN");

        // then
        assertThat(category).isEqualTo(ProductCategory.Other);
    }

    private ProductCategory invokeResolveProductCategory(String mfn) throws Exception {
        Method method = MarketplaceOrderImporter.class.getDeclaredMethod("resolveProductCategory", String.class);
        method.setAccessible(true);
        return (ProductCategory) method.invoke(marketplaceOrderImporter, mfn);
    }
}
