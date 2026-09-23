package pl.commercelink.taxonomy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.pim.api.CategoryMatchRequest;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.taxonomy.mapping.CategoryMappingCache;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonomyCategoryMatchSchedulerTest {

    private static final int NO_LIMIT = 1000;

    @Mock
    private PendingCategorizationRepository pendingRepository;

    @Mock
    private TaxonomyCache catalog;

    @Mock
    private PimCatalog pimCatalog;

    @Mock
    private CategoryMappingCache mappingCache;

    private final List<PendingCategorization> pendingRows = new ArrayList<>();
    private final Map<String, Taxonomy> catalogRows = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(pendingRepository.findAll()).thenAnswer(invocation -> List.copyOf(pendingRows));
        lenient().when(pendingRepository.count()).thenAnswer(invocation -> pendingRows.size());
        lenient().when(pendingRepository.find(anyString()))
                .thenAnswer(invocation -> rowOf(invocation.getArgument(0)));
        lenient().doAnswer(invocation -> {
            PendingCategorization row = rowOf(invocation.getArgument(0));
            row.setAttempts(row.attemptCount() + 1);
            return null;
        }).when(pendingRepository).recordAttempt(anyString());
        lenient().doAnswer(invocation -> {
            pendingRows.removeIf(row -> row.getMfn().equals(invocation.getArgument(0)));
            return null;
        }).when(pendingRepository).remove(anyString());

        lenient().when(catalog.findByMfns(any())).thenAnswer(invocation -> {
            Collection<String> wanted = invocation.getArgument(0);
            Map<String, Taxonomy> found = new HashMap<>();
            wanted.stream().filter(catalogRows::containsKey).forEach(mfn -> found.put(mfn, catalogRows.get(mfn)));
            return found;
        });
        lenient().when(catalog.updateCategory(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            String mfn = invocation.getArgument(0);
            Taxonomy stored = catalogRows.get(mfn);
            if (!Taxonomy.hasCategory(stored)) {
                catalogRows.put(mfn, new Taxonomy(stored.ean(), stored.mfn(), stored.brand(), stored.name(),
                        invocation.getArgument(1), stored.dataAccuracyScore(), stored.netWeightInGrams(),
                        stored.grossWeightInGrams(), stored.rawCategory(), invocation.getArgument(2)));
                return true;
            }
            return false;
        });
    }

    @Test
    void everyPendingRowIsSubmittedWhenTheRunLimitAllowsIt() {
        // given
        List<String> pendingMfns = IntStream.range(0, 20).mapToObj(i -> "MFN-P-" + i).toList();
        pendingMfns.forEach(mfn -> givenPending(mfn, null, null));
        catalogRows.put("MFN-CAT", new Taxonomy("1234567890123", "MFN-CAT", "Brand", "Name", "CPU", 5, null, null));

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        ArgumentCaptor<CategoryMatchRequest> captor = ArgumentCaptor.captor();
        verify(pimCatalog, atLeastOnce()).submitCategoryMatch(captor.capture());
        List<String> submitted = captor.getAllValues().stream().map(CategoryMatchRequest::mfn).toList();
        assertThat(submitted).hasSize(20);
        assertThat(Set.copyOf(submitted)).isEqualTo(Set.copyOf(pendingMfns));
    }

    @Test
    void submissionsAreCappedPerRun() {
        // given
        IntStream.range(0, 20).forEach(i -> givenPending("MFN-P-" + i, null, null));

        // when
        scheduler(properties(5)).sweep();

        // then
        verify(pimCatalog, times(5)).submitCategoryMatch(any());
    }

    @Test
    void requestCarriesIdentifiersFromTheCatalogRow() {
        // given
        givenPending("MFN-1", null, null);

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        ArgumentCaptor<CategoryMatchRequest> captor = ArgumentCaptor.captor();
        verify(pimCatalog).submitCategoryMatch(captor.capture());
        CategoryMatchRequest request = captor.getValue();
        assertThat(request.ean()).isEqualTo("1234567890123");
        assertThat(request.mfn()).isEqualTo("MFN-1");
        assertThat(request.brand()).isEqualTo("Brand");
        assertThat(request.name()).isEqualTo("Name");
        assertThat(request.supplier()).isNull();
        assertThat(request.rawCategory()).isNull();
    }

    @Test
    void sweepPassesRawCategoryIntoRequest() {
        // given
        givenPending("MFN-1", null, "Karty graficzne");

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        ArgumentCaptor<CategoryMatchRequest> captor = ArgumentCaptor.captor();
        verify(pimCatalog).submitCategoryMatch(captor.capture());
        assertThat(captor.getValue().rawCategory()).isEqualTo("Karty graficzne");
    }

    @Test
    void sweepAbortsQuietlyWhenSqsIsNotConfigured() {
        // given
        givenPending("MFN-1", null, null);
        doThrow(new IllegalStateException("no sqs")).when(pimCatalog).submitCategoryMatch(any());

        // when / then
        scheduler(properties(NO_LIMIT)).sweep();
    }

    @Test
    void emptyPendingTableDoesNothing() {
        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        verify(pimCatalog, never()).submitCategoryMatch(any());
        verify(catalog, never()).findByMfns(any());
    }

    @Test
    void pendingRowIsDroppedWhenTheCatalogRecordIsAlreadyCategorized() {
        // given
        givenPending("MFN-1", null, null);
        catalogRows.put("MFN-1", new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 5, null, null));

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        verify(pimCatalog, never()).submitCategoryMatch(any());
        verify(pendingRepository).remove("MFN-1");
    }

    @Test
    void pendingRowIsDroppedWhenTheCatalogHasNoRecordAtAll() {
        // given
        pendingRows.add(pendingRow("MFN-GONE", null));

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        verify(pimCatalog, never()).submitCategoryMatch(any());
        verify(pendingRepository).remove("MFN-GONE");
    }

    @Test
    void activeMappingResolvesPendingWithoutSubmit() {
        // given
        String mfn = mfnWhere(residue -> residue != 0);
        givenPending(mfn, "Acme", "Karty graficzne");
        when(mappingCache.findActive("Acme", "Karty graficzne"))
                .thenReturn(Optional.of(new CategoryMappingCache.ActiveMapping("301", "GPU")));

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        assertThat(catalogRows.get(mfn).category()).isEqualTo("GPU");
        assertThat(catalogRows.get(mfn).categoryId()).isEqualTo("301");
        verify(pimCatalog, never()).submitCategoryMatch(any());
        verify(pendingRepository).remove(mfn);
    }

    @Test
    void mappingMissFallsBackToSubmit() {
        // given
        String mfn = mfnWhere(residue -> residue != 0);
        givenPending(mfn, "Acme", "Karty graficzne");
        when(mappingCache.findActive("Acme", "Karty graficzne")).thenReturn(Optional.empty());

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        verify(pimCatalog).submitCategoryMatch(any());
        assertThat(catalogRows.get(mfn).category()).isNull();
    }

    @Test
    void trickleMfnBypassesMappingCache() {
        // given
        String mfn = mfnWhere(residue -> residue == 0);
        givenPending(mfn, "Acme", "Karty graficzne");

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        verify(mappingCache, never()).findActive(any(), any());
        verify(pimCatalog).submitCategoryMatch(any());
    }

    @Test
    void pendingWithoutSupplierSkipsMappingLookup() {
        // given
        String mfn = mfnWhere(residue -> residue != 0);
        givenPending(mfn, null, "Karty graficzne");

        // when
        scheduler(properties(NO_LIMIT)).sweep();

        // then
        verify(mappingCache, never()).findActive(any(), any());
        verify(pimCatalog).submitCategoryMatch(any());
    }

    @Test
    void givesUpAfterMaxAttemptsSubmissions() {
        // given
        givenPending("MFN-1", null, null);
        TaxonomyCategoryMatchScheduler scheduler = scheduler(properties(NO_LIMIT));

        // when
        for (int i = 0; i < 6; i++) {
            scheduler.sweep();
        }

        // then
        verify(pimCatalog, times(4)).submitCategoryMatch(any());
    }

    @Test
    void zeroMaxAttemptsKeepsSubmittingForever() {
        // given
        givenPending("MFN-1", null, null);
        TaxonomyCategoryMatchScheduler scheduler = scheduler(new TaxonomyCategoryMatchProperties(
                NO_LIMIT, NO_LIMIT, new TaxonomyCategoryMatchProperties.Mapping(5, 0.9, 0.9, 20), 0));

        // when
        for (int i = 0; i < 10; i++) {
            scheduler.sweep();
        }

        // then
        verify(pimCatalog, times(10)).submitCategoryMatch(any());
    }

    @Test
    void failedSubmitDoesNotCountAsAttempt() {
        // given
        givenPending("MFN-1", null, null);
        doThrow(new IllegalStateException("no sqs")).doNothing().when(pimCatalog).submitCategoryMatch(any());
        TaxonomyCategoryMatchScheduler scheduler = scheduler(properties(NO_LIMIT));

        // when
        for (int i = 0; i < 6; i++) {
            scheduler.sweep();
        }

        // then
        verify(pimCatalog, times(5)).submitCategoryMatch(any());
    }

    private TaxonomyCategoryMatchScheduler scheduler(TaxonomyCategoryMatchProperties properties) {
        return new TaxonomyCategoryMatchScheduler(pendingRepository, catalog, pimCatalog, properties,
                new TaxonomyCategoryEnrichment(catalog, pendingRepository, properties, mappingCache), mappingCache);
    }

    private static TaxonomyCategoryMatchProperties properties(int maxSubmissionsPerRun) {
        return new TaxonomyCategoryMatchProperties(NO_LIMIT, maxSubmissionsPerRun);
    }

    private void givenPending(String mfn, String supplier, String rawCategory) {
        pendingRows.add(pendingRow(mfn, supplier));
        catalogRows.put(mfn, new Taxonomy("1234567890123", mfn, "Brand", "Name", null, 5, null, null, rawCategory));
    }

    private static PendingCategorization pendingRow(String mfn, String supplier) {
        PendingCategorization row = new PendingCategorization();
        row.setMfn(mfn);
        row.setSupplier(supplier);
        row.setAttempts(0);
        row.setAddedAt(LocalDateTime.now());
        return row;
    }

    private PendingCategorization rowOf(String mfn) {
        return pendingRows.stream().filter(row -> row.getMfn().equals(mfn)).findFirst().orElseThrow();
    }

    private static String mfnWhere(IntPredicate trickleResidue) {
        return IntStream.range(0, 1000).mapToObj(i -> "MFN-" + i)
                .filter(mfn -> trickleResidue.test(Math.floorMod(mfn.hashCode(), 20)))
                .findFirst().orElseThrow();
    }
}
