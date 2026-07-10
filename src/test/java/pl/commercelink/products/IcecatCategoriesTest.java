package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IcecatCategoriesTest {

    @Mock
    private PimCatalog pimCatalog;

    private IcecatCategories icecatCategories() {
        return new IcecatCategories(pimCatalog);
    }

    @Test
    void topLevelNamesAreSortedWithPolishCollation() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Meble", "Furniture"),
                new PimCategory("2", null, "Łóżka", "Beds"),
                new PimCategory("3", null, "Dom", "Home"),
                new PimCategory("4", "3", "Dywany", "Carpets")
        ));

        // when
        List<String> names = icecatCategories().topLevelNames();

        // then
        assertThat(names).containsExactly("Dom", "Łóżka", "Meble");
    }

    @Test
    void topLevelNamesAreEmptyWhenCatalogHasNoCategories() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of());

        // when / then
        assertThat(icecatCategories().topLevelNames()).isEmpty();
    }

    @Test
    void leafNamesUnderReturnsOnlyLeavesOfEnabledTopLevels() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "Home"),
                new PimCategory("2", "1", "Meble do domu", "Home Furniture"),
                new PimCategory("3", "2", "Stoły", "Tables"),
                new PimCategory("4", "2", "Krzesła", "Chairs"),
                new PimCategory("5", null, "Biuro", "Office"),
                new PimCategory("6", "5", "Artykuły biurowe", "Office Supplies")
        ));

        // when
        List<String> names = icecatCategories().leafNamesUnder(List.of("Dom"));

        // then
        assertThat(names).containsExactly("Krzesła", "Stoły");
    }

    @Test
    void leafNamesUnderCombinesAndSortsLeavesFromManyTopLevels() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "Home"),
                new PimCategory("2", "1", "Stoły", "Tables"),
                new PimCategory("3", null, "Biuro", "Office"),
                new PimCategory("4", "3", "Ławki", "Benches")
        ));

        // when
        List<String> names = icecatCategories().leafNamesUnder(List.of("Dom", "Biuro"));

        // then
        assertThat(names).containsExactly("Ławki", "Stoły");
    }

    @Test
    void leafNamesUnderIgnoresUnknownTopLevelNames() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "Home"),
                new PimCategory("2", "1", "Stoły", "Tables")
        ));

        // when / then
        assertThat(icecatCategories().leafNamesUnder(List.of("Computers"))).isEmpty();
    }
}
