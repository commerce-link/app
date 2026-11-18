package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PimEanOnlyCodesTest {

    @Test
    void flaggedGlobalEntryGivesOnePairPerCode() {
        // given
        PimEntry entry = entry("Mova", true, null, true, "SHINE10", "SHINE10-EU");

        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(entry));

        // then
        assertThat(codes.contains("mova", "SHINE10")).isTrue();
        assertThat(codes.contains("mova", "SHINE10-EU")).isTrue();
        assertThat(codes.size()).isEqualTo(2);
    }

    @Test
    void brandIsComparedLikeAnOfferBrand() {
        // given
        PimEntry entry = entry(" MOVA ", true, null, true, "SHINE10");
        InventoryItem offer = offer("Mova", "SHINE10");

        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(entry));

        // then
        assertThat(codes.contains(offer.brandKey(), offer.mfn())).isTrue();
    }

    @Test
    void codeIsUnifiedLikeAnOfferCode() {
        // given
        PimEntry entry = entry("Mova", true, null, true, "shine 10");
        InventoryItem offer = offer("Mova", "SHINE10");

        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(entry));

        // then
        assertThat(codes.contains(offer.brandKey(), offer.mfn())).isTrue();
    }

    @Test
    void unflaggedEntryGivesNothing() {
        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(entry("Mova", false, null, true, "SHINE10")));

        // then
        assertThat(codes.size()).isZero();
    }

    @Test
    void storeEntryGivesNothingEvenWhenFlagged() {
        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(entry("Mova", true, "s1", true, "SHINE10")));

        // then
        assertThat(codes.size()).isZero();
    }

    @Test
    void entryWithoutBrandGivesNothing() {
        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(
                entry(null, true, null, true, "SHINE10"),
                entry("  ", true, null, true, "SHINE11")));

        // then
        assertThat(codes.size()).isZero();
    }

    @Test
    void noNameBrandGivesNothing() {
        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(entry(" noname ", true, null, true, "SHINE10")));

        // then
        assertThat(codes.size()).isZero();
    }

    @Test
    void entryWithoutCodesGivesNothing() {
        // given
        PimEntry onlyGtin = new PimEntry("pim-1", List.of(new PimIdentifier("5901234567801", PimIdentifierType.GTIN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, true);

        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(onlyGtin, entry("Mova", true, null, true, " ")));

        // then
        assertThat(codes.size()).isZero();
    }

    @Test
    void entryWithNullIdentifiersGivesNothing() {
        // given
        PimEntry noIdentifiers = new PimEntry("pim-1", null, "Mova", "Name", null, null, true, null, null, null, null,
                null, true);
        PimEntry nullCode = new PimEntry("pim-2", List.of(new PimIdentifier(null, PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, null, null, true);

        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(noIdentifiers, nullCode));

        // then
        assertThat(codes.size()).isZero();
    }

    @Test
    void unapprovedFlaggedEntryStillCounts() {
        // when
        VariantCodes codes = PimEanOnlyCodes.of(List.of(entry("Mova", true, null, false, "SHINE10")));

        // then
        assertThat(codes.contains("mova", "SHINE10")).isTrue();
    }

    @Test
    void ofStoreTakesOnlyThisStoresFlaggedEntries() {
        // given
        List<PimEntry> entries = List.of(
                entry("Mova", true, "s1", true, "SHINE10"),
                entry("Mova", true, "s2", true, "SHINE11"),
                entry("Mova", true, null, true, "SHINE12"),
                entry("Mova", false, "s1", true, "SHINE13"));

        // when
        VariantCodes codes = PimEanOnlyCodes.ofStore("s1", entries);

        // then
        assertThat(codes.contains("mova", "SHINE10")).isTrue();
        assertThat(codes.size()).isEqualTo(1);
    }

    @Test
    void ofStoreTakesClones() {
        // given
        PimEntry clone = new PimEntry("pim-1", List.of(new PimIdentifier("SHINE10", PimIdentifierType.MPN)),
                "Mova", "Name", null, null, true, null, null, null, "s1", "pim-global", true);

        // when
        VariantCodes codes = PimEanOnlyCodes.ofStore("s1", List.of(clone));

        // then
        assertThat(codes.contains("mova", "SHINE10")).isTrue();
    }

    @Test
    void ofStoreSkipsUnmatchableBrandAndBlankCode() {
        // when
        VariantCodes codes = PimEanOnlyCodes.ofStore("s1", List.of(
                entry(null, true, "s1", true, "SHINE10"),
                entry(" noname ", true, "s1", true, "SHINE11"),
                entry("Mova", true, "s1", true, " ")));

        // then
        assertThat(codes.size()).isZero();
    }

    @Test
    void ofStoreNormalizesBrandAndCode() {
        // given
        InventoryItem offer = offer("Mova", "SHINE10");

        // when
        VariantCodes codes = PimEanOnlyCodes.ofStore("s1", List.of(entry(" MOVA ", true, "s1", false, "shine 10")));

        // then
        assertThat(codes.contains(offer.brandKey(), offer.mfn())).isTrue();
    }

    private static PimEntry entry(String brand, boolean eanOnly, String ownerStoreId, boolean approved,
                                  String... mpns) {
        List<PimIdentifier> identifiers = new ArrayList<>();
        identifiers.add(new PimIdentifier("5901234567801", PimIdentifierType.GTIN));
        for (String mpn : mpns) {
            identifiers.add(new PimIdentifier(mpn, PimIdentifierType.MPN));
        }
        return new PimEntry("pim-1", identifiers, brand, "Name", null, null, approved, null, null, null,
                ownerStoreId, null, eanOnly);
    }

    private static InventoryItem offer(String brand, String mfn) {
        return new InventoryItem(brand, null, "5901234567801", mfn, "X", 10.0, "PLN", 1, 1, true, false, false);
    }
}
