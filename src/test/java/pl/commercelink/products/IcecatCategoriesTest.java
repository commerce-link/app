package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.util.Arrays;
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
                new PimCategory("1", null, "Meble", "pl"),
                new PimCategory("2", null, "Łóżka", "pl"),
                new PimCategory("3", null, "Dom", "pl"),
                new PimCategory("4", "3", "Dywany", "pl")
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
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble do domu", "pl"),
                new PimCategory("3", "2", "Stoły", "pl"),
                new PimCategory("4", "2", "Krzesła", "pl"),
                new PimCategory("5", null, "Biuro", "pl"),
                new PimCategory("6", "5", "Artykuły biurowe", "pl")
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
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl"),
                new PimCategory("3", null, "Biuro", "pl"),
                new PimCategory("4", "3", "Ławki", "pl")
        ));

        // when
        List<String> names = icecatCategories().leafNamesUnder(List.of("Dom", "Biuro"));

        // then
        assertThat(names).containsExactly("Ławki", "Stoły");
    }

    @Test
    void topLevelNamesIgnoresCategoriesServedInAnotherLanguage() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", null, "Home", "en")
        ));

        // when
        List<String> names = icecatCategories().topLevelNames();

        // then
        assertThat(names).containsExactly("Dom");
    }

    @Test
    void topLevelNamesAreEmptyWhenPimServesCategoriesWithoutAName() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, null, null),
                new PimCategory("2", null, null, null)
        ));

        // when / then
        assertThat(icecatCategories().topLevelNames()).isEmpty();
    }

    @Test
    void leafNamesUnderAreEmptyWhenPimServesCategoriesWithoutAName() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, null, null),
                new PimCategory("2", "1", null, null)
        ));

        // when / then
        assertThat(icecatCategories().leafNamesUnder(List.of("Dom"))).isEmpty();
    }

    @Test
    void leafNamesUnderIgnoresUnknownTopLevelNames() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when / then
        assertThat(icecatCategories().leafNamesUnder(List.of("Computers"))).isEmpty();
    }

    @Test
    void categoryOptionsKeepCurrentValuesThatAreNotUnderEnabledTopLevels() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<String> options = icecatCategories().categoryOptions(List.of("Dom"), List.of("CPU"));

        // then
        assertThat(options).containsExactly("CPU", "Stoły");
    }

    @Test
    void categoryOptionsSortCurrentValuesWithPolishCollation() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble", "pl")
        ));

        // when
        List<String> options = icecatCategories().categoryOptions(List.of("Dom"), List.of("Łóżka"));

        // then
        assertThat(options).containsExactly("Łóżka", "Meble");
    }

    @Test
    void categoryOptionsDoNotDuplicateCurrentValuesAlreadyAvailable() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<String> options = icecatCategories().categoryOptions(List.of("Dom"), List.of("Stoły", "Stoły"));

        // then
        assertThat(options).containsExactly("Stoły");
    }

    @Test
    void categoryOptionsIgnoreMissingCurrentValues() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<String> options = icecatCategories().categoryOptions(
                List.of("Dom"), Arrays.asList(null, "", "  "));

        // then
        assertThat(options).containsExactly("Stoły");
    }

    @Test
    void categoryOptionsSkipServicesBecauseThePickerAlwaysOffersItSeparately() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<String> options = icecatCategories().categoryOptions(List.of("Dom"), List.of("Services"));

        // then
        assertThat(options).containsExactly("Stoły");
    }
}
