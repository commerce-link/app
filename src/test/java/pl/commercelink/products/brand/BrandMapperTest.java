package pl.commercelink.products.brand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.Brand;
import pl.commercelink.pim.api.PimCatalog;

import java.util.List;
import java.util.Optional;

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
    void findBrandResolvesByAliasCaseInsensitively() {
        Optional<Brand> result = brandMapper.findBrand("Asustek");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Asus");
    }

    @Test
    void findBrandResolvesByCanonicalName() {
        Optional<Brand> result = brandMapper.findBrand("Apple");
        assertThat(result).isPresent();
        assertThat(result.get().name()).isEqualTo("Apple");
    }

    @Test
    void findBrandReturnsEmptyForUnknownInput() {
        assertThat(brandMapper.findBrand("UnknownBrand")).isEmpty();
    }

    @Test
    void findBrandReturnsEmptyForNullOrBlank() {
        assertThat(brandMapper.findBrand(null)).isEmpty();
        assertThat(brandMapper.findBrand("  ")).isEmpty();
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
}
