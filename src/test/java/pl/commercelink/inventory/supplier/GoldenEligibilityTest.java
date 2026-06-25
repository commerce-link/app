package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.InventoryRepository;
import pl.commercelink.inventory.supplier.api.CsvRowParser;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.ParsedRow;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.taxonomy.ProductCategory;
import pl.commercelink.taxonomy.TaxonomyCache;
import pl.commercelink.taxonomy.TaxonomyRepository;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GOLDEN characterization (surface: Eligibility / drop-sites - CSV loader).
 *
 * Freezes the eligibility gate in {@link CsvProductFeedLoader#parseRows}:
 * {@code item != null && taxonomy != null && taxonomy.isProcessable() && item.isSellable()}
 * guards BOTH {@code taxonomyCache.add(...)} AND {@code res.add(item)}.
 *
 * Four synthetic rows are fed:
 *  (a) passing row,
 *  (b) dropped because {@code !item.isSellable()} (sellable flag false),
 *  (c) dropped because {@code taxonomy.category == Other} ({@code isProcessable()==false}),
 *  (d) MFN collision with (a) (same mfn, tie on dataAccuracyScore).
 *
 * Dependencies (repo/correction/cleanup) are mocked; TaxonomyCache is REAL
 * (over a mocked TaxonomyRepository, onStartUp NOT invoked).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoldenEligibilityTest {

    private static final String SUPPLIER = "Action";

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private StoreFeedRepository storeFeedRepository;
    @Mock
    private DataCorrection dataCorrection;
    @Mock
    private DataCleanup dataCleanup;
    @Mock
    private TaxonomyRepository taxonomyRepository;
    @Mock
    private CsvRowParser parser;

    private TaxonomyCache taxonomyCache;
    private CsvProductFeedLoader loader;

    @BeforeEach
    void setUp() {
        // given - REAL TaxonomyCache over a mocked repository; onStartUp() is NOT called
        taxonomyCache = new TaxonomyCache(taxonomyRepository);
        loader = new CsvProductFeedLoader(
                inventoryRepository, storeFeedRepository, dataCorrection, dataCleanup, taxonomyCache);

        // DataCorrection mocked as pass-through (item -> item, taxonomy -> taxonomy)
        when(dataCorrection.run(any(InventoryItem.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(dataCorrection.run(any(Taxonomy.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // DataCleanup mocked as pass-through (list -> list)
        when(dataCleanup.run(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private InventoryItem sellableItem(String ean, String mfn) {
        return new InventoryItem(ean, mfn, 10.0, "PLN", 5, 2, SUPPLIER, true, true, false);
    }

    private Taxonomy processableTaxonomy(String mfn, String name, int score) {
        return new Taxonomy("4711111111111", mfn, "BrandX", name, ProductCategory.CPU, score);
    }

    private void feedRows(ParsedRow... rows) throws IOException {
        // 4 data rows behind a header line; CSVLoader skips the header (skipLines=1)
        String csv = "ean;mfn;qty\n" + "r1\nr2\nr3\nr4\n";
        when(inventoryRepository.canRead(SUPPLIER)).thenReturn(true);
        Reader reader = new StringReader(csv);
        when(inventoryRepository.read(SUPPLIER)).thenReturn(reader);

        Optional<ParsedRow> first = Optional.of(rows[0]);
        Optional<ParsedRow>[] rest = new Optional[rows.length - 1];
        for (int i = 1; i < rows.length; i++) {
            rest[i - 1] = Optional.of(rows[i]);
        }
        when(parser.tryParse(any())).thenReturn(first, rest);
    }

    @Test
    void gateGuardsBothCacheAndResultList_acrossFourSyntheticRows() throws IOException {
        // given
        // (a) passing row
        InventoryItem itemA = sellableItem("4711111111111", "MFN1");
        Taxonomy taxA = processableTaxonomy("MFN1", "Passing-A", 5);
        // (b) dropped by !isSellable (sellable flag is false)
        InventoryItem itemB = new InventoryItem("4722222222222", "MFN2", 10.0, "PLN", 5, 2, SUPPLIER, false, true, false);
        Taxonomy taxB = processableTaxonomy("MFN2", "Unsellable-B", 5);
        // (c) dropped by category == Other (isProcessable() == false)
        InventoryItem itemC = sellableItem("4733333333333", "MFN3");
        Taxonomy taxC = new Taxonomy("4733333333333", "MFN3", "BrandX", "Other-C", ProductCategory.Other, 5);
        // (d) MFN collision with (a): same mfn, tie on score, distinct ean (distinct uuid)
        InventoryItem itemD = sellableItem("4744444444444", "MFN1");
        Taxonomy taxD = processableTaxonomy("MFN1", "Collision-D", 5);

        feedRows(
                new ParsedRow(itemA, taxA),
                new ParsedRow(itemB, taxB),
                new ParsedRow(itemC, taxC),
                new ParsedRow(itemD, taxD));

        // when
        List<InventoryItem> result = loader.fetch(parser, ';', SUPPLIER);

        // then - only (a) and (d) clear the gate into the result list; (b) and (c) dropped
        assertEquals(2, result.size());
        assertTrue(result.contains(itemA));
        assertTrue(result.contains(itemD));
        assertTrue(result.stream().noneMatch(i -> i.mfn().equals("MFN2")));
        assertTrue(result.stream().noneMatch(i -> i.mfn().equals("MFN3")));

        // then - cache: only the eligible taxonomies were added; MFN1 collided (one entry)
        assertEquals(1, taxonomyCache.size());
        assertNull(taxonomyCache.findByMfn("MFN2"));
        assertNull(taxonomyCache.findByMfn("MFN3"));

        // then - on a score tie the INCOMING (d) wins the merge for the colliding mfn
        Taxonomy cached = taxonomyCache.findByMfn("MFN1");
        assertNotNull(cached);
        assertEquals("Collision-D", cached.name());
    }

    @Test
    void eligibleSetIsIndependentOfFeedOrder() throws IOException {
        // given - same four rows fed in a different order: (d), (c), (b), (a)
        InventoryItem itemA = sellableItem("4711111111111", "MFN1");
        Taxonomy taxA = processableTaxonomy("MFN1", "Passing-A", 5);
        InventoryItem itemB = new InventoryItem("4722222222222", "MFN2", 10.0, "PLN", 5, 2, SUPPLIER, false, true, false);
        Taxonomy taxB = processableTaxonomy("MFN2", "Unsellable-B", 5);
        InventoryItem itemC = sellableItem("4733333333333", "MFN3");
        Taxonomy taxC = new Taxonomy("4733333333333", "MFN3", "BrandX", "Other-C", ProductCategory.Other, 5);
        InventoryItem itemD = sellableItem("4744444444444", "MFN1");
        Taxonomy taxD = processableTaxonomy("MFN1", "Collision-D", 5);

        feedRows(
                new ParsedRow(itemD, taxD),
                new ParsedRow(itemC, taxC),
                new ParsedRow(itemB, taxB),
                new ParsedRow(itemA, taxA));

        // when
        List<InventoryItem> result = loader.fetch(parser, ';', SUPPLIER);

        // then - the eligible set (keys) is the same regardless of order
        assertEquals(2, result.size());
        assertTrue(result.contains(itemA));
        assertTrue(result.contains(itemD));
        assertEquals(1, taxonomyCache.size());
        assertNotNull(taxonomyCache.findByMfn("MFN1"));
    }
}
