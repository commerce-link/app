package pl.commercelink.products.brand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.Brand;
import pl.commercelink.pim.api.PimCatalog;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandMapperTest {

    private PimCatalog pimCatalog;
    private BrandMapper brandMapper;

    @BeforeEach
    void setUp() {
        pimCatalog = mock(PimCatalog.class);
        when(pimCatalog.allBrands()).thenReturn(List.of(
                new Brand("Asus", List.of("asus", "asustek")),
                new Brand("Apple", List.of("apple"))
        ));
        brandMapper = new BrandMapper(pimCatalog);
    }

    @Test
    void unifyBrandResolvesAnAliasCaseInsensitively() {
        // when / then
        assertThat(brandMapper.unifyBrand(" ASUSTEK ")).isEqualTo("Asus");
    }

    @Test
    void unifyBrandResolvesTheCanonicalNameNotListedAmongAliases() {
        // given
        when(pimCatalog.allBrands()).thenReturn(List.of(new Brand("Western Digital", List.of("sandisk"))));

        // when
        String result = brandMapper.unifyBrand("WESTERN DIGITAL");

        // then
        assertThat(result).isEqualTo("Western Digital");
    }

    @Test
    void unifyBrandLeavesBlankInputUnchanged() {
        // when / then
        assertThat(brandMapper.unifyBrand("  ")).isEqualTo("  ");
    }

    @Test
    void unifyBrandReturnsCanonicalNameForKnownInput() {
        assertThat(brandMapper.unifyBrand("asustek")).isEqualTo("Asus");
    }

    @Test
    void unifyBrandReturnsOriginalForUnknownInput() {
        assertThat(brandMapper.unifyBrand("Frobnitz")).isEqualTo("Frobnitz");
    }

    @Test
    void unifyBrandReturnsNullForNull() {
        assertThat(brandMapper.unifyBrand(null)).isNull();
    }

    @Test
    void brandListChangedInPimIsPickedUp() {
        // given
        brandMapper.unifyBrand("asustek");
        when(pimCatalog.allBrands()).thenReturn(List.of(new Brand("ASUSTeK Computer", List.of("asustek"))));

        // when
        String result = brandMapper.unifyBrand("Asustek");

        // then
        assertThat(result).isEqualTo("ASUSTeK Computer");
    }

    @Test
    void firstBrandOnTheListWinsAnAliasSharedByTwoBrands() {
        // given
        when(pimCatalog.allBrands()).thenReturn(List.of(
                new Brand("Western Digital", List.of("sandisk")),
                new Brand("SanDisk", List.of())
        ));

        // when
        String result = brandMapper.unifyBrand("SanDisk");

        // then
        assertThat(result).isEqualTo("Western Digital");
    }
}
