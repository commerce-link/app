package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.inventory.supplier.api.Taxonomy;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.products.brand.BrandMapper;
import pl.commercelink.taxonomy.ProductCategory;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * GOLDEN characterization (surface: 13 - EAN-repair, DataCorrection NOT approved-gated).
 *
 * Freezes TWO distinct gating behaviours of {@link DataCorrection}:
 *
 * (1) EAN-repair via {@code resolveCorrectEanForMfn} uses {@code pimCatalog.findByMpn(mfn)}
 *     WITHOUT {@code .filter(PimEntry::approved)}. A blank or placeholder ("1111111111111")
 *     EAN is repaired to the first GTIN of the matched PIM entry EVEN WHEN that entry is
 *     {@code approved == false}.
 *
 * (2) Category override in {@code run(Taxonomy)} goes through {@code resolveFromPim}, which
 *     IS approved-gated ({@code findByGtinOrMpn(...).filter(PimEntry::approved)}). The PIM
 *     category overrides the feed category ONLY when the entry is approved AND its category is
 *     non-null AND not {@code ProductCategory.Other}.
 *
 * All collaborators (PimCatalog, BrandMapper) are mocked; {@code new DataCorrection(...)}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoldenDataCorrectionEanRepairTest {

    private static final String MFN = "MFN1";
    private static final String GTIN = "5901234567890";

    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private BrandMapper brandMapper;
    @InjectMocks
    private DataCorrection dataCorrection;

    private PimEntry entry(boolean approved, ProductCategory category, String... gtins) {
        List<PimIdentifier> ids = java.util.Arrays.stream(gtins)
                .map(g -> new PimIdentifier(g, PimIdentifierType.GTIN))
                .toList();
        return new PimEntry("pim-id", ids, "PimBrand", "PimName", category,
                "subcategory", approved, null, null);
    }

    @Test
    void blankEanRepairedFromPimEntryEvenWhenNotApproved() {
        // given - PIM entry matched by MPN is NOT approved but carries a GTIN
        PimEntry notApproved = entry(false, ProductCategory.Other, GTIN);
        when(pimCatalog.findByMpn(MFN)).thenReturn(Optional.of(notApproved));
        InventoryItem blankEan = new InventoryItem("", MFN, 10.0, "PLN", 5, 2, "Action");

        // when - run(InventoryItem) repairs a blank EAN
        InventoryItem result = dataCorrection.run(blankEan);

        // then - EAN repaired from the (non-approved) PIM entry's first GTIN
        assertThat(result.ean()).isEqualTo(GTIN);
    }

    @Test
    void placeholderEanRepairedFromPimEntryEvenWhenNotApproved() {
        // given - placeholder EAN "1111111111111" and a NON-approved PIM entry with a GTIN
        PimEntry notApproved = entry(false, ProductCategory.Other, GTIN);
        when(pimCatalog.findByMpn(MFN)).thenReturn(Optional.of(notApproved));
        InventoryItem placeholder = new InventoryItem("1111111111111", MFN, 10.0, "PLN", 5, 2, "Action");

        // when
        InventoryItem result = dataCorrection.run(placeholder);

        // then - placeholder treated as missing and repaired despite approved == false
        assertThat(result.ean()).isEqualTo(GTIN);
    }

    @Test
    void validEanIsNotRepaired() {
        // given - a valid (non-blank, non-placeholder) EAN
        InventoryItem valid = new InventoryItem("4711111111111", MFN, 10.0, "PLN", 5, 2, "Action");

        // when
        InventoryItem result = dataCorrection.run(valid);

        // then - left untouched; findByMpn is never consulted
        assertThat(result.ean()).isEqualTo("4711111111111");
    }

    @Test
    void categoryOverriddenWhenApprovedAndCategoryIsNotOther() {
        // given - approved PIM entry with a concrete category (Desktops)
        lenient().when(brandMapper.unifyBrand(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        PimEntry approvedDesktops = entry(true, ProductCategory.Desktops, GTIN);
        when(pimCatalog.findByGtinOrMpn(GTIN, MFN)).thenReturn(Optional.of(approvedDesktops));
        Taxonomy feed = new Taxonomy(GTIN, MFN, "FeedBrand", "FeedName",
                ProductCategory.Laptops, 5, 100, 200);

        // when
        Taxonomy result = dataCorrection.run(feed);

        // then - feed category Laptops overridden by approved PIM category Desktops
        assertThat(result.category()).isEqualTo(ProductCategory.Desktops);
    }

    @Test
    void categoryNotOverriddenWhenApprovedButPimCategoryIsOther() {
        // given - approved PIM entry whose category is Other
        lenient().when(brandMapper.unifyBrand(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        PimEntry approvedOther = entry(true, ProductCategory.Other, GTIN);
        when(pimCatalog.findByGtinOrMpn(GTIN, MFN)).thenReturn(Optional.of(approvedOther));
        Taxonomy feed = new Taxonomy(GTIN, MFN, "FeedBrand", "FeedName",
                ProductCategory.Laptops, 5, 100, 200);

        // when
        Taxonomy result = dataCorrection.run(feed);

        // then - PIM category Other is ignored; feed category Laptops kept
        assertThat(result.category()).isEqualTo(ProductCategory.Laptops);
    }

    @Test
    void categoryNotOverriddenWhenPimEntryNotApproved() {
        // given - PIM entry with a concrete category but NOT approved (filtered out)
        lenient().when(brandMapper.unifyBrand(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        PimEntry notApprovedDesktops = entry(false, ProductCategory.Desktops, GTIN);
        when(pimCatalog.findByGtinOrMpn(GTIN, MFN)).thenReturn(Optional.of(notApprovedDesktops));
        Taxonomy feed = new Taxonomy(GTIN, MFN, "FeedBrand", "FeedName",
                ProductCategory.Laptops, 5, 100, 200);

        // when
        Taxonomy result = dataCorrection.run(feed);

        // then - non-approved entry is filtered; feed category Laptops kept
        assertThat(result.category()).isEqualTo(ProductCategory.Laptops);
    }
}
