package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.inventory.supplier.api.Taxonomy;

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
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "Other", 5, 999, 1999);
        PimEntry pim = pimEntry(true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(7000);
        assertThat(result.grossWeightInGrams()).isEqualTo(9000);
    }

    @Test
    void usesPimNetAndFeedGrossWhenPimHasOnlyNet() {
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "Other", 5, null, 1999);
        PimEntry pim = pimEntry(true, 7000, null);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(7000);
        assertThat(result.grossWeightInGrams()).isEqualTo(1999);
    }

    @Test
    void usesFeedWeightsWhenPimHasNoneOfTheTwo() {
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "Other", 5, 100, 200);
        PimEntry pim = pimEntry(true, null, null);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void usesFeedWeightsWhenPimEntryIsNotApproved() {
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "Other", 5, 100, 200);
        PimEntry unapproved = pimEntry(false, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(unapproved));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void usesFeedWeightsWhenNoPimEntryFound() {
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "Other", 5, 100, 200);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.empty());

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.netWeightInGrams()).isEqualTo(100);
        assertThat(result.grossWeightInGrams()).isEqualTo(200);
    }

    @Test
    void scoreIsZeroedWhenPimMatchesApproved() {
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "Other", 5, 100, 200);
        PimEntry pim = pimEntry(true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        Taxonomy result = dataCorrection.run(fromFeed);

        assertThat(result.dataAccuracyScore()).isZero();
    }

    @Test
    void keepsFeedCategoryWhenPimCategoryIsUnknown() {
        // given
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "CPU", 5, 100, 200);
        PimEntry pim = pimEntry("Smartwatches", true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.category()).isEqualTo("CPU");
    }

    @Test
    void overridesFeedCategoryWhenPimCategoryIsKnown() {
        // given
        Taxonomy fromFeed = new Taxonomy("1234567890123", "MFN", "FeedBrand", "FeedName",
                "CPU", 5, 100, 200);
        PimEntry pim = pimEntry("GPU", true, 7000, 9000);
        when(pimCatalog.findByGtinOrMpn("1234567890123", "MFN")).thenReturn(Optional.of(pim));

        // when
        Taxonomy result = dataCorrection.run(fromFeed);

        // then
        assertThat(result.category()).isEqualTo("GPU");
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
