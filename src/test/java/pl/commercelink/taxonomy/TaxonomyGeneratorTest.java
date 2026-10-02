package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyGeneratorTest {

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private TaxonomyRepository taxonomyRepository;
    @Mock
    private PimCatalog pimCatalog;
    @InjectMocks
    private TaxonomyGenerator generator;

    @Test
    void savesOnlyCategorizedTaxonomies() {
        // given
        Taxonomy categorized = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 5, null, null);
        Taxonomy pending = new Taxonomy("1234567890124", "MFN-2", "Brand", "Name", null, 5, null, null);
        when(taxonomyCache.getTaxonomies()).thenReturn(List.of(categorized, pending));
        when(pimCatalog.allCategories()).thenReturn(List.of());

        // when
        generator.handleMessage("generate");

        // then
        verify(taxonomyRepository).save(List.of(categorized));
    }

    @Test
    void refreshesCategoryNameWhenIdMapsToDifferentName() {
        // given
        Taxonomy taxonomy = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Stara nazwa", 5, 100, 200,
                "Raw category", "CAT-1");
        Map<String, String> idToName = Map.of("CAT-1", "Nowa nazwa");

        // when
        Taxonomy refreshed = TaxonomyGenerator.refreshCategoryName(taxonomy, idToName);

        // then
        assertThat(refreshed.category()).isEqualTo("Nowa nazwa");
        assertThat(refreshed.categoryId()).isEqualTo("CAT-1");
        assertThat(refreshed.ean()).isEqualTo("1234567890123");
        assertThat(refreshed.mfn()).isEqualTo("MFN-1");
        assertThat(refreshed.brand()).isEqualTo("Brand");
        assertThat(refreshed.name()).isEqualTo("Name");
        assertThat(refreshed.dataAccuracyScore()).isEqualTo(5);
        assertThat(refreshed.netWeightInGrams()).isEqualTo(100);
        assertThat(refreshed.grossWeightInGrams()).isEqualTo(200);
        assertThat(refreshed.rawCategory()).isEqualTo("Raw category");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("noOpRefreshes")
    void keepsTheSameTaxonomyWhenThereIsNothingToRefresh(String reason, Taxonomy taxonomy, Map<String, String> idToName) {
        // when
        Taxonomy refreshed = TaxonomyGenerator.refreshCategoryName(taxonomy, idToName);

        // then
        assertThat(refreshed).isSameAs(taxonomy);
    }

    static Stream<Arguments> noOpRefreshes() {
        return Stream.of(
                Arguments.of("id is absent from the map",
                        new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Stara nazwa", 5, 100, 200,
                                "Raw category", "CAT-UNKNOWN"),
                        Map.of("CAT-1", "Nowa nazwa")),
                Arguments.of("category id is null",
                        new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Stara nazwa", 5, 100, 200,
                                "Raw category", null),
                        Map.of("CAT-1", "Nowa nazwa")),
                Arguments.of("mapped name is already current",
                        new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "Aktualna nazwa", 5, 100, 200,
                                "Raw category", "CAT-1"),
                        Map.of("CAT-1", "Aktualna nazwa")));
    }
}
