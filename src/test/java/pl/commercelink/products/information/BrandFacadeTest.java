package pl.commercelink.products.information;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrandFacadeTest {

    @AfterEach
    void resetFacade() {
        BrandFacade.initialize(null);
    }

    @Test
    void unifyPassesThroughWhenDelegateNotInitialized() {
        BrandFacade.initialize(null);

        assertThat(BrandFacade.unify("Asustek")).isEqualTo("Asustek");
        assertThat(BrandFacade.unify(null)).isNull();
    }

    @Test
    void strengthReturnsDefaultWhenDelegateNotInitialized() {
        BrandFacade.initialize(null);

        assertThat(BrandFacade.strength("Apple")).isEqualTo(1);
    }

    @Test
    void unifyDelegatesToCatalog() {
        PimCatalog catalog = mock(PimCatalog.class);
        when(catalog.unifyBrand("Asustek")).thenReturn("Asus");
        BrandFacade.initialize(catalog);

        assertThat(BrandFacade.unify("Asustek")).isEqualTo("Asus");
    }

    @Test
    void strengthDelegatesToCatalog() {
        PimCatalog catalog = mock(PimCatalog.class);
        when(catalog.brandStrength("Apple")).thenReturn(2);
        BrandFacade.initialize(catalog);

        assertThat(BrandFacade.strength("Apple")).isEqualTo(2);
    }
}
