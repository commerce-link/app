package pl.commercelink.taxonomy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TaxonomyMergeTest {

    @Test
    void singleCandidateKeepsItsWeight() {
        // given
        TaxonomyMerge merge = merge();

        // when
        merge.add(taxonomy("MFN-1", 5, 1300));

        // then
        assertEquals(1300, merge.current("MFN-1").netWeightInGrams());
    }

    @Test
    void betterScoreCandidateWithoutWeightKeepsPreviousWeight() {
        // given
        TaxonomyMerge merge = merge(taxonomy("MFN-1", 10, 1300));

        // when
        merge.add(taxonomyNamed("MFN-1", 1, null, "BetterName"));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void betterScoreCandidateWithWeightReplacesEverything() {
        // given
        TaxonomyMerge merge = merge(taxonomy("MFN-1", 10, 1300));

        // when
        merge.add(taxonomyNamed("MFN-1", 1, 1500, "BetterName"));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals("BetterName", result.name());
        assertEquals(1500, result.netWeightInGrams());
    }

    @Test
    void worseScoreCandidateWithWeightFillsMissingWeightOnly() {
        // given
        TaxonomyMerge merge = merge(taxonomyNamed("MFN-1", 1, null, "BestName"));

        // when
        merge.add(taxonomyNamed("MFN-1", 10, 1300, "WorseName"));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals("BestName", result.name());
        assertEquals(1300, result.netWeightInGrams());
    }

    @Test
    void twoSourcesWithWeightLowerScoreWins() {
        // given
        TaxonomyMerge merge = merge(taxonomy("MFN-1", 10, 1500));

        // when
        merge.add(taxonomy("MFN-1", 5, 1300));

        // then
        assertEquals(1300, merge.current("MFN-1").netWeightInGrams());
    }

    @Test
    void blankMfnIsNoop() {
        // given
        TaxonomyMerge merge = merge();

        // when
        merge.add(taxonomy("", 5, 1300));
        merge.add(taxonomy("   ", 5, 1300));

        // then
        assertThat(merge.changed()).isEmpty();
    }

    @Test
    void mergesNetFromOneAndGrossFromOtherIndependently() {
        // given
        TaxonomyMerge merge = merge(new Taxonomy("E", "MFN1", "B", "N", null, 5, 100, null));

        // when
        merge.add(new Taxonomy("E", "MFN1", "B", "N", null, 10, null, 200));

        // then
        Taxonomy result = merge.current("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void prefersWeightFromLowestScorePerDimension() {
        // given
        TaxonomyMerge merge = merge(new Taxonomy("E", "MFN1", "B", "N", null, 9, 100, 200));

        // when
        merge.add(new Taxonomy("E", "MFN1", "B", "N", null, 2, 999, null));

        // then
        Taxonomy result = merge.current("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(999);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void incomingWinsTieBreakForBothDimensions() {
        // given
        TaxonomyMerge merge = merge(new Taxonomy("E", "MFN1", "B", "N", null, 5, 100, 200));

        // when
        merge.add(new Taxonomy("E", "MFN1", "B", "N", null, 5, 150, 250));

        // then
        Taxonomy result = merge.current("MFN1");
        assertThat(result.netWeightInGrams()).isEqualTo(150);
        assertThat(result.grossWeightInGrams()).isEqualTo(250);
    }

    @Test
    void categorizedEntryIsNotClobberedByBetterScoreUncategorized() {
        // given
        TaxonomyMerge merge = merge(categorized("MFN-1", "CPU", 10));

        // when
        merge.add(uncategorized("MFN-1", 1));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals(10, result.dataAccuracyScore());
    }

    @Test
    void incomingCategorizedEntryReplacesUncategorizedDespiteWorseScore() {
        // given
        TaxonomyMerge merge = merge(uncategorized("MFN-1", 1));

        // when
        merge.add(categorized("MFN-1", "CPU", 10));

        // then
        assertEquals("CPU", merge.current("MFN-1").category());
    }

    @Test
    void blankCategoryIsTreatedAsUncategorized() {
        // given
        TaxonomyMerge merge = merge(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "", 1, null, null));

        // when
        merge.add(categorized("MFN-1", "CPU", 10));

        // then
        assertEquals("CPU", merge.current("MFN-1").category());
    }

    @Test
    void twoCategorizedEntriesStillMergeByScore() {
        // given
        TaxonomyMerge merge = merge(categorized("MFN-1", "CPU", 10));

        // when
        merge.add(categorized("MFN-1", "GPU", 1));

        // then
        assertEquals("GPU", merge.current("MFN-1").category());
    }

    @Test
    void categorizedWinnerStillAdoptsWeightFromUncategorizedEntry() {
        // given
        TaxonomyMerge merge = merge(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 1, 777, null));

        // when
        merge.add(categorized("MFN-1", "CPU", 10));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals("CPU", result.category());
        assertEquals(777, result.netWeightInGrams());
    }

    @Test
    void mergePreservesRawCategoryOfWinningPendingEntry() {
        // given
        TaxonomyMerge merge = merge(
                new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 10, null, 500, "First"));

        // when
        merge.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", null, 1, 300, null, "Second"));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals(300, result.netWeightInGrams());
        assertEquals(500, result.grossWeightInGrams());
        assertEquals("Second", result.rawCategory());
    }

    @Test
    void mergePreservesCategoryIdOfWinningEntry() {
        // given
        TaxonomyMerge merge = merge(
                new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 10, null, 500, "Raw", "111"));

        // when
        merge.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name", "CPU", 1, 300, null, "Raw", "999"));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals(300, result.netWeightInGrams());
        assertEquals(500, result.grossWeightInGrams());
        assertEquals("999", result.categoryId());
    }

    @Test
    void pimCorrectionWithoutIdReplacesPreviouslyResolvedCategoryId() {
        // given
        TaxonomyMerge merge = merge(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name",
                "Karty graficzne", 3, null, null, "Raw", "1613"));

        // when
        merge.add(new Taxonomy("1234567890123", "MFN-1", "Brand", "Name",
                "Karty graficzne", 0, null, null, "Raw", null));

        // then
        Taxonomy result = merge.current("MFN-1");
        assertEquals("Karty graficzne", result.category());
        assertNull(result.categoryId());
    }

    @Test
    void mergePreservesAllFieldsExactlyIncludingLeadingZeroEan() {
        // given
        TaxonomyMerge merge = merge();
        Taxonomy input = new Taxonomy("0012345678905", "MFN-ZERO", "Brand", "Name",
                "Laptops", 5, 1300, 1400, "RawCat", "301");

        // when
        merge.add(input);

        // then
        assertThat(merge.current("MFN-ZERO")).isEqualTo(input);
    }

    @Test
    void repeatedCandidatesWithinOneChunkMergeIncrementally() {
        // given
        TaxonomyMerge merge = merge();

        // when
        merge.add(uncategorized("MFN-1", 1));
        merge.add(categorized("MFN-1", "CPU", 10));

        // then
        assertEquals("CPU", merge.current("MFN-1").category());
        assertThat(merge.changed()).hasSize(1);
    }

    @Test
    void candidateEqualToTheStoredRecordIsNotReportedAsChanged() {
        // given
        Taxonomy stored = categorized("MFN-1", "CPU", 10);
        TaxonomyMerge merge = merge(stored);

        // when
        merge.add(stored);

        // then
        assertThat(merge.changed()).isEmpty();
    }

    @Test
    void changedReportsOnlyRecordsThatDifferFromTheStoredOnes() {
        // given
        Taxonomy untouched = categorized("MFN-1", "CPU", 10);
        TaxonomyMerge merge = merge(untouched, categorized("MFN-2", "GPU", 10));

        // when
        merge.add(untouched);
        merge.add(categorized("MFN-2", "SSD", 1));
        merge.add(categorized("MFN-3", "RAM", 1));

        // then
        assertThat(merge.changed())
                .extracting(Taxonomy::mfn)
                .containsExactlyInAnyOrder("MFN-2", "MFN-3");
    }

    private static TaxonomyMerge merge(Taxonomy... stored) {
        Map<String, Taxonomy> byMfn = new HashMap<>();
        List.of(stored).forEach(taxonomy -> byMfn.put(taxonomy.mfn(), taxonomy));
        return new TaxonomyMerge(byMfn);
    }

    private static Taxonomy taxonomy(String mfn, int score, Integer weight) {
        return taxonomyNamed(mfn, score, weight, "Name");
    }

    private static Taxonomy taxonomyNamed(String mfn, int score, Integer weight, String name) {
        return new Taxonomy("1234567890123", mfn, "Brand", name, "Laptops", score, weight, null);
    }

    private static Taxonomy categorized(String mfn, String category, int score) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", category, score, null, null);
    }

    private static Taxonomy uncategorized(String mfn, int score) {
        return new Taxonomy("1234567890123", mfn, "Brand", "Name", null, score, null, null);
    }
}
