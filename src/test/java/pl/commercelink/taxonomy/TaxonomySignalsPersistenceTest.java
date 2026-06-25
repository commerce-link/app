package pl.commercelink.taxonomy;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.starter.csv.CSVLoader;
import pl.commercelink.starter.storage.FileStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomySignalsPersistenceTest {

    private static final String SIGNAL_A = "VENDOR_CATEGORY:Obudowa";
    private static final String SIGNAL_B = "BRAND:Acme";

    @Mock
    private TaxonomyRepository taxonomyRepository;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private BrandMapper brandMapper;
    @InjectMocks
    private TaxonomyRepository repository;

    @Test
    void signalsSurviveAWeightMergeRebuild() {
        // given - two same-MFN taxonomies with differing weights force withWeights() to rebuild the winner
        when(taxonomyRepository.loadNewest()).thenReturn(Pair.of("N/A", new ArrayList<>()));
        TaxonomyCache cache = new TaxonomyCache(taxonomyRepository);
        cache.onStartUp();
        cache.add(new Taxonomy("E1", "MFN1", "B", "N", ProductCategory.Other, 5, 100, null, null, List.of(SIGNAL_A, SIGNAL_B)));
        cache.add(new Taxonomy("E2", "MFN1", "B", "N", ProductCategory.Other, 9, null, 200, null, List.of(SIGNAL_A)));

        // when
        Taxonomy merged = cache.findByMfn("MFN1");

        // then - winner is the lower-score taxonomy; its signals survive the rebuild
        assertThat(merged.signals()).containsExactly(SIGNAL_A, SIGNAL_B);
        assertThat(merged.netWeightInGrams()).isEqualTo(100);
        assertThat(merged.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void csvRoundTripPreservesSignals() {
        // given
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name",
                ProductCategory.Other, 1, null, null, "Cables356k", List.of(SIGNAL_A, SIGNAL_B));

        // when
        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        Taxonomy parsed = TaxonomyParser.fromCsvRow(loader.readHeadersAndRows(';').getSecond().get(0));

        // then
        assertThat(parsed.signals()).containsExactly(SIGNAL_A, SIGNAL_B);
        assertThat(parsed.categoryKey()).isEqualTo("Cables356k");
    }

    @Test
    void csvRoundTripWithEmptySignalsStaysEmpty() {
        // given
        Taxonomy original = new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", ProductCategory.CPU, 1);

        // when
        byte[] csv = TaxonomyParser.toCsv(List.of(original));
        CSVLoader loader = new CSVLoader(new InputStreamReader(new ByteArrayInputStream(csv)));
        Taxonomy parsed = TaxonomyParser.fromCsvRow(loader.readHeadersAndRows(';').getSecond().get(0));

        // then
        assertThat(parsed.signals()).isEmpty();
        assertThat(parsed.category()).isEqualTo(ProductCategory.CPU);
    }

    @Test
    void oldCsvRowWithoutSignalsColumnYieldsEmptySignals() {
        // given - legacy 9-column row (category_key but no signals column)
        String[] row = {"123", "MFN", "Brand", "Name", "Other", "5", "", "", "Cables356k"};

        // when
        Taxonomy parsed = TaxonomyParser.fromCsvRow(row);

        // then
        assertThat(parsed.signals()).isEmpty();
        assertThat(parsed.categoryKey()).isEqualTo("Cables356k");
    }

    @Test
    void loadNewestPreservesSignalsFromCsv() throws Exception {
        // given
        setField(repository, "bucketName", "datalake");
        when(brandMapper.unifyBrand(anyString())).thenAnswer(inv -> inv.getArgument(0));
        String csv = "ean;mfn;brand;name;category;data_accuracy_score;net_weight_g;gross_weight_g;category_key;signals\n"
                + "1234567890123;MFN-1;Brand;Name;Other;5;;;Cables356k;" + SIGNAL_A;
        when(fileStorage.findNewest("datalake", "taxonomy/"))
                .thenReturn(Pair.of("taxonomy/2026-06-25.csv",
                        new InputStreamReader(new ByteArrayInputStream(csv.getBytes()))));

        // when
        Pair<String, List<Taxonomy>> result = repository.loadNewest();

        // then
        assertThat(result.getRight()).hasSize(1);
        assertThat(result.getRight().get(0).signals()).containsExactly(SIGNAL_A);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
