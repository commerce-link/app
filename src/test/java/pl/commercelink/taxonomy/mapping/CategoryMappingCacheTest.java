package pl.commercelink.taxonomy.mapping;

import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimCategory;
import pl.commercelink.taxonomy.TaxonomyCategoryMatchProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryMappingCacheTest {

    @Mock
    private CategoryMappingRepository repository;

    @Mock
    private PimCatalog pimCatalog;

    private CategoryMappingCache mappingCache;
    private final Map<String, CategoryMapping> store = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(repository.find(any(), any()))
                .thenAnswer(inv -> store.get(inv.getArgument(0) + "|" + inv.getArgument(1)));
        lenient().doAnswer(inv -> {
            CategoryMapping mapping = inv.getArgument(0);
            store.put(mapping.getSupplier() + "|" + mapping.getRawCategory(), mapping);
            return null;
        }).when(repository).save(any(CategoryMapping.class));
        lenient().when(pimCatalog.allCategories()).thenReturn(categoryTree());
        mappingCache = new CategoryMappingCache(repository, new TaxonomyCategoryMatchProperties(100, 300000), pimCatalog);
    }

    private static List<PimCategory> categoryTree() {
        return List.of(
                new PimCategory("2833", null, "Komputery i urządzenia peryferyjne", "pl"),
                new PimCategory("206", "2833", "Przechowywanie danych", "pl"),
                new PimCategory("301", "206", "GPU", "pl"),
                new PimCategory("999", "206", "Inna", "pl"),
                new PimCategory("777", "206", "Cokolwiek", "pl"));
    }

    @Test
    void promotesMappingAfterConsensusAndServesItFromMirror() {
        // when
        for (int i = 0; i < 5; i++) {
            mappingCache.recordSample("Acme", "Karty graficzne", "301", "GPU");
        }

        // then
        Optional<CategoryMappingCache.ActiveMapping> active = mappingCache.findActive("Acme", "Karty graficzne");
        assertThat(active).contains(new CategoryMappingCache.ActiveMapping("301", "GPU"));
        verify(repository, atLeastOnce()).save(any(CategoryMapping.class));
    }

    @Test
    void findActiveNormalizesRawCategoryKey() {
        // given
        for (int i = 0; i < 5; i++) {
            mappingCache.recordSample("Acme", "  Karty   GRAFICZNE ", "301", "GPU");
        }

        // when / then
        assertThat(mappingCache.findActive("Acme", "karty graficzne")).isPresent();
    }

    @Test
    void learningMappingIsNotServed() {
        // given
        mappingCache.recordSample("Acme", "Karty graficzne", "301", "GPU");

        // when / then
        assertThat(mappingCache.findActive("Acme", "Karty graficzne")).isEmpty();
    }

    @Test
    void demotedMappingDisappearsFromMirror() {
        // given
        for (int i = 0; i < 5; i++) {
            mappingCache.recordSample("Acme", "Karty graficzne", "301", "GPU");
        }
        assertThat(mappingCache.findActive("Acme", "Karty graficzne")).isPresent();

        // when
        mappingCache.recordSample("Acme", "Karty graficzne", "999", "Inna");

        // then
        assertThat(mappingCache.findActive("Acme", "Karty graficzne")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "non-leaf category, Dyski, 206, Przechowywanie danych, 5",
            "root category, Komputery, 2833, Komputery i urządzenia peryferyjne, 1",
            "unknown category, Dyski, 555, Nieznana, 1"
    })
    void sampleOfAnInvalidCategoryIsIgnored(String rule, String rawCategory, String categoryId, String categoryName, int samples) {
        // when
        for (int i = 0; i < samples; i++) {
            mappingCache.recordSample("Acme", rawCategory, categoryId, categoryName);
        }

        // then
        verify(repository, never()).save(any(CategoryMapping.class));
        assertThat(mappingCache.findActive("Acme", rawCategory)).isEmpty();
    }

    @Test
    void samplesAreIgnoredWhenCategoryTreeUnavailable() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of());

        // when
        mappingCache.recordSample("Acme", "Karty graficzne", "301", "GPU");

        // then
        verify(repository, never()).save(any(CategoryMapping.class));
    }

    @Test
    void leafIndexRebuildsOnceTreeBecomesAvailable() {
        // given
        when(pimCatalog.allCategories()).thenReturn(List.of()).thenReturn(categoryTree());
        mappingCache.recordSample("Acme", "Karty graficzne", "301", "GPU");
        verify(repository, never()).save(any(CategoryMapping.class));

        // when
        mappingCache.recordSample("Acme", "Karty graficzne", "301", "GPU");

        // then
        verify(repository, atLeastOnce()).save(any(CategoryMapping.class));
    }

    @Test
    void blankInputsAreIgnored() {
        // when
        mappingCache.recordSample(" ", "Karty graficzne", "301", "GPU");
        mappingCache.recordSample("Acme", " ", "301", "GPU");
        mappingCache.recordSample("Acme", "Karty graficzne", " ", "GPU");

        // then
        verify(repository, never()).save(any(CategoryMapping.class));
        assertThat(mappingCache.findActive(null, "Karty graficzne")).isEmpty();
        assertThat(mappingCache.findActive("Acme", null)).isEmpty();
    }

    @Test
    void repositoryFailureDoesNotPropagate() {
        // given
        doThrow(new RuntimeException("dynamo down")).when(repository).save(any(CategoryMapping.class));

        // when / then
        mappingCache.recordSample("Acme", "Karty graficzne", "301", "GPU");
    }

    @Test
    void startUpLoadsOnlyActiveMappings() {
        // given
        CategoryMapping active = CategoryMapping.learning("Acme", "karty graficzne", "Karty graficzne");
        for (int i = 0; i < 5; i++) {
            active.recordSample("301", "GPU", 5, 0.9);
        }
        CategoryMapping learning = CategoryMapping.learning("Acme", "akcesoria", "Akcesoria");
        learning.recordSample("777", "Cokolwiek", 5, 0.9);
        when(repository.findAll()).thenReturn(List.of(active, learning));

        // when
        mappingCache.onStartUp();

        // then
        assertThat(mappingCache.findActive("Acme", "Karty graficzne")).isPresent();
        assertThat(mappingCache.findActive("Acme", "Akcesoria")).isEmpty();
    }

    @Test
    void startUpToleratesMissingTable() {
        // given
        when(repository.findAll()).thenThrow(new ResourceNotFoundException("no table"));

        // when
        mappingCache.onStartUp();

        // then
        assertThat(mappingCache.findActive("Acme", "Karty graficzne")).isEmpty();
    }
}
