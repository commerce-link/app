package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaxonomyCacheBrandByEanTest {

    private TaxonomyRepository repository;
    private TaxonomyCache cache;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(TaxonomyRepository.class);
        Mockito.when(repository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        cache = new TaxonomyCache(repository);
        cache.onStartUp();
    }

    @Test
    void indexesBrandByEanOfAddedTaxonomy() {
        // given
        Taxonomy taxonomy = taxonomy("5900000000011", "1234", "Logitech", 1);

        // when
        cache.add(taxonomy);

        // then
        assertThat(cache.brandOf("5900000000011")).isEqualTo("Logitech");
    }

    @Test
    void keepsBothBrandsWhenProductsShareAMfn() {
        // given
        Taxonomy mouse = taxonomy("5900000000011", "1234", "Logitech", 1);
        Taxonomy laptop = taxonomy("5900000000028", "1234", "Dell", 2);

        // when
        cache.add(mouse);
        cache.add(laptop);

        // then
        assertThat(cache.brandOf("5900000000011")).isEqualTo("Logitech");
        assertThat(cache.brandOf("5900000000028")).isEqualTo("Dell");
    }

    @Test
    void prefersBrandOfTheMoreAccurateTaxonomyForTheSameEan() {
        // given
        cache.add(taxonomy("5900000000011", "1234", "Logi", 10));

        // when
        cache.add(taxonomy("5900000000011", "1234", "Logitech", 1));
        cache.add(taxonomy("5900000000011", "1234", "Logitek", 20));

        // then
        assertThat(cache.brandOf("5900000000011")).isEqualTo("Logitech");
    }

    @Test
    void ignoresTaxonomyWithoutBrandOrEan() {
        // given
        Taxonomy withoutBrand = taxonomy("5900000000011", "1234", " ", 1);
        Taxonomy withoutEan = taxonomy("", "5678", "Dell", 1);

        // when
        cache.add(withoutBrand);
        cache.add(withoutEan);

        // then
        assertThat(cache.brandOf("5900000000011")).isNull();
        assertThat(cache.brandOf("")).isNull();
        assertThat(cache.brandOf(null)).isNull();
    }

    @Test
    void indexesBrandsOfTaxonomiesLoadedAtStartUp() {
        // given
        Mockito.when(repository.loadNewest()).thenReturn(Pair.of("file.csv", List.of(
                taxonomy("5900000000011", "1234", "Logitech", 1),
                taxonomy("5900000000028", "1234", "Dell", 2))));
        TaxonomyCache loaded = new TaxonomyCache(repository);

        // when
        loaded.onStartUp();

        // then
        assertThat(loaded.brandOf("5900000000011")).isEqualTo("Logitech");
        assertThat(loaded.brandOf("5900000000028")).isEqualTo("Dell");
    }

    @Test
    void returnsNullForUnknownEan() {
        // when / then
        assertThat(cache.brandOf("5900000000099")).isNull();
    }

    private Taxonomy taxonomy(String ean, String mfn, String brand, int score) {
        return new Taxonomy(ean, mfn, brand, "Name", "Category", score, null, null);
    }
}
