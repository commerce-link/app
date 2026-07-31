package pl.commercelink.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PimCategoryOptionsTest {

    @Mock
    private PimCatalog pimCatalog;

    private PimCategoryOptions pimCategoryOptions() {
        return new PimCategoryOptions(pimCatalog);
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
        List<String> names = pimCategoryOptions().topLevelNames();

        // then
        assertThat(names).containsExactly("Dom", "Łóżka", "Meble");
    }

    @Test
    void topLevelNamesAreEmptyWhenCatalogHasNoCategories() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of());

        // when / then
        assertThat(pimCategoryOptions().topLevelNames()).isEmpty();
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
        List<String> names = pimCategoryOptions().leafNamesUnder(List.of("Dom"));

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
        List<String> names = pimCategoryOptions().leafNamesUnder(List.of("Dom", "Biuro"));

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
        List<String> names = pimCategoryOptions().topLevelNames();

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
        assertThat(pimCategoryOptions().topLevelNames()).isEmpty();
    }

    @Test
    void leafNamesUnderAreEmptyWhenPimServesCategoriesWithoutAName() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, null, null),
                new PimCategory("2", "1", null, null)
        ));

        // when / then
        assertThat(pimCategoryOptions().leafNamesUnder(List.of("Dom"))).isEmpty();
    }

    @Test
    void leafNamesUnderIgnoresUnknownTopLevelNames() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when / then
        assertThat(pimCategoryOptions().leafNamesUnder(List.of("Computers"))).isEmpty();
    }

    @Test
    void categoryOptionsKeepCurrentValuesThatAreNotUnderEnabledTopLevels() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<String> options = pimCategoryOptions().categoryOptions(List.of("Dom"), List.of("CPU"));

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
        List<String> options = pimCategoryOptions().categoryOptions(List.of("Dom"), List.of("Łóżka"));

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
        List<String> options = pimCategoryOptions().categoryOptions(List.of("Dom"), List.of("Stoły", "Stoły"));

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
        List<String> options = pimCategoryOptions().categoryOptions(
                List.of("Dom"), Arrays.asList(null, "", "  "));

        // then
        assertThat(options).containsExactly("Stoły");
    }

    @Test
    void categoryOptionsTreatLegacyServicesValueLikeAnyOtherUnknownCurrentValue() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<String> options = pimCategoryOptions().categoryOptions(List.of("Dom"), List.of("Services"));

        // then
        assertThat(options).containsExactly("Services", "Stoły");
    }

    @Test
    void categoryOptionsIgnoreNullCurrentValueOfServiceDefinitions() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<String> options = pimCategoryOptions().categoryOptions(List.of("Dom"), Collections.singletonList(null));

        // then
        assertThat(options).containsExactly("Stoły");
    }

    @Test
    void leafOptionsUnderReturnsIdAndNameForLeavesOnly() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble do domu", "pl"),
                new PimCategory("3", "2", "Stoły", "pl"),
                new PimCategory("4", "2", "Krzesła", "pl")
        ));

        // when
        List<CategoryOption> options = pimCategoryOptions().leafOptionsUnder(List.of("Dom"));

        // then
        assertThat(options).containsExactly(
                new CategoryOption("4", "Krzesła"),
                new CategoryOption("3", "Stoły"));
    }

    @Test
    void leafOptionsUnderIgnoresOtherLanguages() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl"),
                new PimCategory("3", null, "Home", "en"),
                new PimCategory("4", "3", "Tables", "en")
        ));

        // when
        List<CategoryOption> options = pimCategoryOptions().leafOptionsUnder(List.of("Dom"));

        // then
        assertThat(options).containsExactly(new CategoryOption("2", "Stoły"));
    }

    @Test
    void categoryOptionsByIdAppendCurrentIdsOutsideEnabledTopLevels() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl"),
                new PimCategory("9", null, "Biuro", "pl"),
                new PimCategory("10", "9", "Ławki", "pl")
        ));

        // when
        List<CategoryOption> options = pimCategoryOptions().categoryOptionsById(List.of("Dom"), List.of("10"));

        // then
        assertThat(options).containsExactly(
                new CategoryOption("10", "Ławki"),
                new CategoryOption("2", "Stoły"));
    }

    @Test
    void categoryOptionsByIdDoNotDuplicateCurrentIdsAlreadyAvailable() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<CategoryOption> options = pimCategoryOptions().categoryOptionsById(List.of("Dom"), List.of("2", "2"));

        // then
        assertThat(options).containsExactly(new CategoryOption("2", "Stoły"));
    }

    @Test
    void categoryOptionsByIdIgnoreUnresolvableCurrentIds() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Stoły", "pl")
        ));

        // when
        List<CategoryOption> options = pimCategoryOptions().categoryOptionsById(List.of("Dom"), List.of("nope"));

        // then
        assertThat(options).containsExactly(new CategoryOption("2", "Stoły"));
    }

    @Test
    void categoryNamesIsAReusableResolverBuiltFromOnePimScan() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble", "pl"),
                new PimCategory("3", "1", "Łóżka", "pl")
        ));
        PimCategoryOptions options = pimCategoryOptions();

        // when
        CategoryNames categoryNames = options.categoryNames();

        // then
        assertThat(categoryNames.namesOf(List.of("2"))).containsExactly("Meble");
        assertThat(categoryNames.joinedNamesOf(List.of("2", "3"))).isEqualTo("Łóżka, Meble");
        assertThat(categoryNames.selectionOf(List.of("3")).categoryIds()).containsExactly("3");
        verify(pimCatalog, times(1)).allCategories();
    }

    @Test
    void namesOfResolvesIdsInPolishCollationOrder() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble", "pl"),
                new PimCategory("3", "1", "Łóżka", "pl")
        ));

        // when
        List<String> names = pimCategoryOptions().namesOf(List.of("2", "3"));

        // then
        assertThat(names).containsExactly("Łóżka", "Meble");
    }

    @Test
    void namesOfSkipsUnresolvableIds() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble", "pl")
        ));

        // when
        List<String> names = pimCategoryOptions().namesOf(List.of("2", "missing"));

        // then
        assertThat(names).containsExactly("Meble");
    }

    @Test
    void namesOfResolvesInternalNodesToo() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble", "pl"),
                new PimCategory("3", "2", "Stoły", "pl")
        ));

        // when
        List<String> names = pimCategoryOptions().namesOf(List.of("2"));

        // then
        assertThat(names).containsExactly("Meble");
    }

    @Test
    void joinedNamesOfJoinsWithCommaSpace() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of(
                new PimCategory("1", null, "Dom", "pl"),
                new PimCategory("2", "1", "Meble", "pl"),
                new PimCategory("3", "1", "Łóżka", "pl")
        ));

        // when
        String joined = pimCategoryOptions().joinedNamesOf(List.of("2", "3"));

        // then
        assertThat(joined).isEqualTo("Łóżka, Meble");
    }

    @Test
    void joinedNamesOfIsEmptyStringForNoIds() {
        // when / then
        assertThat(pimCategoryOptions().joinedNamesOf(List.of())).isEmpty();
    }

    @Test
    void selectionOfCarriesTheGivenIds() {
        // when
        CategorySelection selection = pimCategoryOptions().selectionOf(List.of("2", "3"));

        // then
        assertThat(selection.categoryIds()).containsExactlyInAnyOrder("2", "3");
    }

    @Test
    void selectionOfIsEmptyForNoIds() {
        // when / then
        assertThat(pimCategoryOptions().selectionOf(List.of()).isEmpty()).isTrue();
    }

    @Test
    void selectionOfSanitisesIdsThroughTheFactory() {
        // when
        CategorySelection selection = pimCategoryOptions().selectionOf(
                Arrays.asList(" 2 ", "", null, "  "));

        // then
        assertThat(selection.categoryIds()).containsExactly("2");
    }
}
