package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.inventory.supplier.api.InventoryItem;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimEntry;
import pl.commercelink.pim.api.PimIdentifier;
import pl.commercelink.pim.api.PimIdentifierType;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Offers with different EANs join through a shared manufacturer code only when they have the same brand:
 * suppliers reuse short codes (e.g. "1234") for unrelated products.
 */
@ExtendWith(MockitoExtension.class)
class InventoryAutoDiscoveryBrandTest {

    private static final String SHORT_MFN = "1234";
    private static final String LONG_MFN = "MX-KEYS-920-009415";
    private static final String MOUSE_EAN = "5900000000011";
    private static final String LAPTOP_EAN = "5900000000028";
    private static final String OTHER_MOUSE_EAN = "5900000000035";

    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimView pimView;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    private InventoryAutoDiscovery autoDiscovery(PimEntry... pimEntries) {
        lenient().when(pimCatalog.withGlobalData()).thenReturn(pimView);
        when(pimView.findAll()).thenReturn(List.of(pimEntries));
        return new InventoryAutoDiscovery(pimCatalog, taxonomyCache, supplierRegistry);
    }

    @Test
    void separatesDifferentProductsSharingAShortMfnWhenBrandsDiffer() {
        // given
        InventoryItem cheapMouse = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");
        InventoryItem expensiveLaptop = item(LAPTOP_EAN, SHORT_MFN, 3000, "B", "Dell");

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(cheapMouse, expensiveLaptop));

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void separatesSeveralOffersOfEachProductSharingAShortMfn() {
        // given
        InventoryItem mouseA = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");
        InventoryItem mouseB = item(MOUSE_EAN, SHORT_MFN, 85, "B", "Logitech");
        InventoryItem laptopA = item(LAPTOP_EAN, SHORT_MFN, 3000, "A", "Dell");
        InventoryItem laptopB = item(LAPTOP_EAN, SHORT_MFN, 3100, "B", "Dell");

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(mouseA, mouseB, laptopA, laptopB));

        // then
        assertThat(result).extracting(m -> List.copyOf(m.getInventoryItems()))
                .containsExactlyInAnyOrder(List.of(mouseA, mouseB), List.of(laptopA, laptopB));
    }

    @Test
    void separatesProductsOfOneSupplierSharingAShortMfnWhenBrandsDiffer() {
        // given
        InventoryItem mouse = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");
        InventoryItem laptop = item(LAPTOP_EAN, SHORT_MFN, 3000, "A", "Dell");

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(mouse, laptop));

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void joinsOffersWithDifferentEansWhenMfnAndBrandAreTheSame() {
        // given
        InventoryItem fromA = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");
        InventoryItem fromB = item(OTHER_MOUSE_EAN, SHORT_MFN, 82, "B", "Logitech");

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(fromA, fromB));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).containsExactlyInAnyOrder(fromA, fromB);
    }

    @Test
    void comparesBrandsIgnoringCaseAndSurroundingSpaces() {
        // given
        InventoryItem fromA = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");
        InventoryItem fromB = item(OTHER_MOUSE_EAN, SHORT_MFN, 82, "B", " LOGITECH ");

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(fromA, fromB));

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    void separatesProductsSharingALongMfnWhenBrandsDiffer() {
        // given
        InventoryItem fromA = item(MOUSE_EAN, LONG_MFN, 80, "A", "Logitech");
        InventoryItem fromB = item(LAPTOP_EAN, LONG_MFN, 85, "B", "Dell");

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(fromA, fromB));

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void joinsOffersWithTheSameEanEvenWhenBrandsDiffer() {
        // given
        InventoryItem fromA = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");
        InventoryItem fromB = item(MOUSE_EAN, "OTHER-9999", 85, "B", "Logi");

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(fromA, fromB));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).containsExactlyInAnyOrder(fromA, fromB);
    }

    @Test
    void joinsOffersWithTheSameMfnWhenNoBrandIsKnown() {
        // given
        InventoryItem fromA = item(MOUSE_EAN, SHORT_MFN, 80, "A", null);
        InventoryItem fromB = item(OTHER_MOUSE_EAN, SHORT_MFN, 82, "B", null);

        // when
        Collection<MatchedInventory> result = autoDiscovery().run(List.of(fromA, fromB));

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    void takesOffersOfThePimEntryBrandThroughItsShortMpn() {
        // given
        PimEntry mousePim = pimEntry("PIM1", "Logitech", gtin("5900000000099"), mpn(SHORT_MFN));
        InventoryItem mouse = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");
        InventoryItem laptop = item(LAPTOP_EAN, SHORT_MFN, 3000, "B", "Dell");

        // when
        Collection<MatchedInventory> result = autoDiscovery(mousePim).run(List.of(mouse, laptop));

        // then
        assertThat(result).hasSize(2);
        MatchedInventory pimGroup = result.stream()
                .filter(m -> "PIM1".equals(m.getInventoryKey().getId()))
                .findFirst().orElseThrow();
        assertThat(pimGroup.getInventoryItems()).containsExactly(mouse);
    }

    @Test
    void takesOffersByMpnRegardlessOfBrandWhenPimEntryHasNone() {
        // given
        PimEntry pimWithoutBrand = pimEntry("PIM1", null, gtin("5900000000099"), mpn(SHORT_MFN));
        InventoryItem mouse = item(MOUSE_EAN, SHORT_MFN, 80, "A", "Logitech");

        // when
        Collection<MatchedInventory> result = autoDiscovery(pimWithoutBrand).run(List.of(mouse));

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getInventoryKey().getId()).isEqualTo("PIM1");
        assertThat(matched.getInventoryItems()).containsExactly(mouse);
    }

    private InventoryItem item(String ean, String mfn, double netPrice, String supplier, String brand) {
        return new InventoryItem(ean, mfn, netPrice, "PLN", 5, 1, supplier, true).withBrand(brand);
    }

    private PimEntry pimEntry(String pimId, String brand, PimIdentifier... identifiers) {
        return new PimEntry(pimId, List.of(identifiers), brand, "Name", "Category", "Subcategory", true, null, null);
    }

    private PimIdentifier gtin(String value) {
        return new PimIdentifier(value, PimIdentifierType.GTIN);
    }

    private PimIdentifier mpn(String value) {
        return new PimIdentifier(value, PimIdentifierType.MPN);
    }
}
