package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.api.SupplierProduct;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.taxonomy.Taxonomy;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DataCorrectionTest {

    private PimCatalog pimCatalog;
    private BrandMapper brandMapper;
    private DataCorrection dataCorrection;

    @BeforeEach
    void setUp() {
        pimCatalog = mock(PimCatalog.class);
        brandMapper = mock(BrandMapper.class);
        when(brandMapper.unifyBrand(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        dataCorrection = new DataCorrection(pimCatalog, brandMapper);
    }

    @Test
    void usesPimNetAndGrossWhenBothPresentAndApproved() {
        SupplierProduct fromFeed = feed(999, 1999);
        PimEntry pim = pimEntry(true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(7000);
        assertThat(result.grossWeightInGrams()).isEqualTo(9000);
    }

    @Test
    void usesPimNetAndFeedGrossWhenPimHasOnlyNet() {
        SupplierProduct fromFeed = feed(null, 1999);
        PimEntry pim = pimEntry(true, 7000, null);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(7000);
        assertThat(result.grossWeightInGrams()).isEqualTo(1999);
    }

    @Test
    void usesFeedWeightsWhenPimHasNoneOfTheTwo() {
        SupplierProduct fromFeed = feed(100, 200);
        PimEntry pim = pimEntry(true, null, null);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void usesFeedWeightsWhenPimEntryIsNotApproved() {
        SupplierProduct fromFeed = feed(100, 200);
        PimEntry unapproved = pimEntry(false, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(unapproved));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void usesFeedWeightsWhenNoPimEntryFound() {
        SupplierProduct fromFeed = feed(100, 200);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.empty());

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void scoreIsZeroedWhenPimMatchesApproved() {
        SupplierProduct fromFeed = feed(100, 200);
        PimEntry pim = pimEntry(true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.dataAccuracyScore()).isZero();
    }

    @Test
    void categoryStaysNullWhenPimCategoryIsBlank() {
        // given
        SupplierProduct fromFeed = feed("CPU", 100, 200);
        PimEntry pim = pimEntry(null, true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.category()).isNull();
    }

    @Test
    void usesPimCategoryName() {
        // given
        SupplierProduct fromFeed = feed("CPU", 100, 200);
        PimEntry pim = pimEntry("Smartwatches", true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.category()).isEqualTo("Smartwatches");
    }

    @Test
    void carriesCategoryAndCategoryIdFromPimWhenPresent() {
        // given
        SupplierProduct fromFeed = feed("rawCPU", 100, 200);
        PimEntry pim = new PimEntry("pim-id", List.of(), "PimBrand", "PimName",
                "Karty graficzne", "subcategory", true, 7000, 9000, "170");
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.category()).isEqualTo("Karty graficzne");
        assertThat(result.categoryId()).isEqualTo("170");
    }

    @Test
    void categoryIdStaysNullWhenPimCategoryIdIsBlank() {
        // given
        SupplierProduct fromFeed = feed("rawCPU", 100, 200);
        PimEntry pim = new PimEntry("pim-id", List.of(), "PimBrand", "PimName",
                "Karty graficzne", "subcategory", true, 7000, 9000, "");
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.categoryId()).isNull();
    }

    @Test
    void resolvesServicesCategoryFromRawCategoryWithoutPim() {
        // given
        SupplierProduct fromFeed = feed("Services", 100, 200);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.empty());

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.category()).isEqualTo("Services");
        assertThat(result.rawCategory()).isEqualTo("Services");
    }

    @Test
    void runPreservesAlreadyNormalizedEanAndMfn() {
        // given
        SupplierProduct fromFeed = new SupplierProduct("012345678905", "mfn 1", "FeedBrand", "FeedName", 5, 100, 200);
        when(pimCatalog.findByGtinOrMpn(anyString(), anyString())).thenReturn(Optional.empty());

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.ean()).isEqualTo("12345678905");
        assertThat(result.mfn()).isEqualTo("MFN1");
    }

    @Test
    void runNormalizesPimCorrectedGtinWithLeadingZeros() {
        // given
        SupplierProduct fromFeed = new SupplierProduct("1111111111111", "MFN", "FeedBrand", "FeedName", 5, 100, 200);
        PimEntry pim = new PimEntry("pim-id",
                List.of(new PimIdentifier("0012345678905", PimIdentifierType.GTIN)),
                "PimBrand", "PimName", null, "subcategory", true, null, null, null);
        when(pimCatalog.findByMpn("MFN")).thenReturn(Optional.of(pim));
        when(pimCatalog.findByGtinOrMpn(anyString(), anyString())).thenReturn(Optional.empty());

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.ean()).isEqualTo("012345678905");
    }

    private SupplierProduct feed(Integer net, Integer gross) {
        return new SupplierProduct("1234567890123", "MFN", "FeedBrand", "FeedName", 5, net, gross);
    }

    private SupplierProduct feed(String rawCategory, Integer net, Integer gross) {
        return new SupplierProduct("1234567890123", "MFN", "FeedBrand", "FeedName", 5, net, gross, rawCategory);
    }

    private PimEntry pimEntry(boolean approved, Integer net, Integer gross) {
        return pimEntry("Other", approved, net, gross);
    }

    private PimEntry pimEntry(String category, boolean approved, Integer net, Integer gross) {
        return new PimEntry(
                "pim-id",
                List.of(),
                "PimBrand",
                "PimName",
                category,
                "subcategory",
                approved,
                net,
                gross
        );
    }
}
