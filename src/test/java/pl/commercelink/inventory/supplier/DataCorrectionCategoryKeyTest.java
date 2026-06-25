package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataCorrectionCategoryKeyTest {

    private PimCatalog pimCatalog;
    private BrandMapper brandMapper;
    private DataCorrection dataCorrection;

    @BeforeEach
    void setUp() {
        pimCatalog = mock(PimCatalog.class);
        brandMapper = mock(BrandMapper.class);
        when(brandMapper.unifyBrand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        dataCorrection = new DataCorrection(pimCatalog, brandMapper);
    }

    @Test
    void feedCategoryKeyIsPreservedWhenNoPimEntry() {
        // given
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "Brand", "Name",
                ProductCategory.Other, 5, null, null, "Cables356k");
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.empty());

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.categoryKey()).isEqualTo("Cables356k");
        assertThat(result.isProcessable()).isTrue();
    }

    @Test
    void approvedPimNonEnumCategoryKeyOverridesAndSurvives() {
        // given
        PimEntry approved = new PimEntry("pim", List.of(), "PimBrand", "PimName",
                "Cables356k", "sub", true, null, null);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(approved));
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "Brand", "Name",
                ProductCategory.Other, 5, 100, 200);

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.categoryKey()).isEqualTo("Cables356k");
        assertThat(result.category()).isEqualTo(ProductCategory.Other);
        assertThat(result.isProcessable()).isTrue();
    }

    @Test
    void approvedPimEnumCategoryKeySetsBothKeyAndEnum() {
        // given
        PimEntry approved = new PimEntry("pim", List.of(), "PimBrand", "PimName",
                "Desktops", "sub", true, null, null);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(approved));
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "Brand", "Name",
                ProductCategory.Laptops, 5, null, null);

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.categoryKey()).isEqualTo("Desktops");
        assertThat(result.category()).isEqualTo(ProductCategory.Desktops);
    }
}
