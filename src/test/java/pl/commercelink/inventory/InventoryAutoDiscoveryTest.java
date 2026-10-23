package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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

@ExtendWith(MockitoExtension.class)
class InventoryAutoDiscoveryTest {

    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimView pimView;
    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    @InjectMocks
    private InventoryAutoDiscovery autoDiscovery;

    @BeforeEach
    void globalInventoryMatchesAgainstTheGlobalView() {
        lenient().when(pimCatalog.withGlobalData()).thenReturn(pimView);
    }

    @Test
    void returnsNothingForEmptyFeed() {
        // given
        when(pimView.findAll()).thenReturn(List.of());

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void keepsUnrelatedItemsApart() {
        // given
        when(pimView.findAll()).thenReturn(List.of());
        InventoryItem first = item("EAN1", "MFN1", 100, "A");
        InventoryItem second = item("EAN2", "MFN2", 200, "A");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(first, second));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(m -> assertThat(m.getInventoryItems()).hasSize(1));
    }

    @Test
    void mergesOffersOfTheSameEanFromDifferentSuppliers() {
        // given
        when(pimView.findAll()).thenReturn(List.of());
        InventoryItem fromA = item("EAN1", "MFN1", 100, "A");
        InventoryItem fromB = item("EAN1", "MFN1", 110, "B");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(fromA, fromB));

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getInventoryItems()).containsExactlyInAnyOrder(fromA, fromB);
        assertThat(matched.getSuppliers()).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void mergesOffersSharingOnlyTheManufacturerCode() {
        // given
        when(pimView.findAll()).thenReturn(List.of());
        InventoryItem fromA = item("EAN1", "MFN1", 100, "A");
        InventoryItem fromB = item("EAN2", "MFN1", 105, "B");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(fromA, fromB));

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getInventoryItems()).containsExactlyInAnyOrder(fromA, fromB);
        assertThat(matched.getEans()).containsExactlyInAnyOrder("EAN1", "EAN2");
    }

    @Test
    void usesPimEntryToJoinItemsWithDifferentIdentifiers() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), gtin("EAN2"), mpn("MFN1"), mpn("MFN2"))));
        InventoryItem first = item("EAN1", "MFN1", 100, "A");
        InventoryItem second = item("EAN2", "MFN2", 105, "A");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(first, second));

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getInventoryItems()).containsExactlyInAnyOrder(first, second);
        assertThat(matched.getInventoryKey().getId()).isEqualTo("PIM1");
    }

    @Test
    void dropsPimEntriesWithoutAnyOffer() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN9"), mpn("MFN9"))));
        InventoryItem unrelated = item("EAN1", "MFN1", 100, "A");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(unrelated));

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getInventoryItems()).containsExactly(unrelated);
        assertThat(matched.getInventoryKey().getId()).isNull();
    }

    @Test
    void attachesItemsMatchingOnlyByEanToPimEntryWhenManufacturerCodeIsUnknownToPim() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), mpn("MFN1"))));
        InventoryItem known = item("EAN1", "MFN1", 100, "A");
        InventoryItem sameEanOtherMfn = item("EAN1", "MFN1-ALT", 102, "B");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(known, sameEanOtherMfn));

        // then
        assertThat(result).hasSize(1);
        MatchedInventory matched = result.iterator().next();
        assertThat(matched.getInventoryItems()).containsExactlyInAnyOrder(known, sameEanOtherMfn);
        assertThat(matched.getInventoryKey().getId()).isEqualTo("PIM1");
        assertThat(matched.getMfnCodes()).contains("MFN1", "MFN1-ALT");
    }

    @Test
    void doesNotSplitOffersFromASingleSupplier() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), gtin("EAN2"), mpn("MFN1"), mpn("MFN2"))));
        InventoryItem cheap = item("EAN1", "MFN1", 100, "A");
        InventoryItem expensive = item("EAN2", "MFN2", 1000, "A");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(cheap, expensive));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).containsExactlyInAnyOrder(cheap, expensive);
    }

    @Test
    void doesNotSplitUniformGroupEvenWhenPricesDiffer() {
        // given
        when(pimView.findAll()).thenReturn(List.of());
        InventoryItem first = item("EAN1", "MFN1", 100, "A");
        InventoryItem second = item("EAN1", "MFN1", 1000, "B");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(first, second));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void splitsMixedGroupIntoCheapAndExpensiveProducts() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), gtin("EAN2"), mpn("MFN1"), mpn("MFN2"))));
        InventoryItem cheapA = item("EAN2", "MFN2", 100, "A");
        InventoryItem cheapB = item("EAN2", "MFN2", 105, "B");
        InventoryItem expensiveA = item("EAN1", "MFN1", 1000, "A");
        InventoryItem expensiveB = item("EAN1", "MFN1", 1010, "B");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(cheapA, cheapB, expensiveA, expensiveB));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(m -> List.copyOf(m.getInventoryItems()))
                .containsExactlyInAnyOrder(List.of(cheapA, cheapB), List.of(expensiveA, expensiveB));
    }

    @Test
    void keepsOnlyTheUsablePriceGroupWhenTheOtherHasASingleItem() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), gtin("EAN2"), mpn("MFN1"), mpn("MFN2"))));
        InventoryItem cheapA = item("EAN2", "MFN2", 100, "A");
        InventoryItem cheapB = item("EAN2", "MFN2", 105, "B");
        InventoryItem strayExpensive = item("EAN1", "MFN1", 1000, "A");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(cheapA, cheapB, strayExpensive));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).containsExactly(cheapA, cheapB);
    }

    @Test
    void keepsGroupTogetherWhenNeitherPriceGroupHasMoreThanOneItem() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), gtin("EAN2"), mpn("MFN1"), mpn("MFN2"))));
        InventoryItem cheap = item("EAN2", "MFN2", 100, "A");
        InventoryItem expensive = item("EAN1", "MFN1", 1000, "B");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(cheap, expensive));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).containsExactlyInAnyOrder(cheap, expensive);
    }

    @Test
    void doesNotSplitWhenPriceDiscriminatorIsBelowThreshold() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), gtin("EAN2"), mpn("MFN1"), mpn("MFN2"))));
        InventoryItem cheapA = item("EAN2", "MFN2", 10, "A");
        InventoryItem cheapB = item("EAN2", "MFN2", 11, "B");
        InventoryItem otherA = item("EAN1", "MFN1", 40, "A");
        InventoryItem otherB = item("EAN1", "MFN1", 41, "B");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(cheapA, cheapB, otherA, otherB));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).hasSize(4);
    }

    @Test
    void dropsOddlyPricedItemsFromTheSurvivingGroup() {
        // given
        when(pimView.findAll()).thenReturn(List.of(pimEntry("PIM1", gtin("EAN1"), gtin("EAN2"), mpn("MFN1"), mpn("MFN2"))));
        InventoryItem cheapA = item("EAN2", "MFN2", 100, "A");
        InventoryItem cheapB = item("EAN2", "MFN2", 100, "B");
        InventoryItem cheapC = item("EAN2", "MFN2", 100, "C");
        InventoryItem tooLow = item("EAN2", "MFN2", 20, "D");
        InventoryItem strayExpensive = item("EAN1", "MFN1", 1000, "A");

        // when
        Collection<MatchedInventory> result = autoDiscovery.run(List.of(cheapA, cheapB, cheapC, tooLow, strayExpensive));

        // then
        assertThat(result).hasSize(1);
        assertThat(result.iterator().next().getInventoryItems()).containsExactlyInAnyOrder(cheapA, cheapB, cheapC);
    }

    private InventoryItem item(String ean, String mfn, double netPrice, String supplier) {
        return new InventoryItem(ean, mfn, netPrice, "PLN", 5, 1, supplier, true);
    }

    private PimEntry pimEntry(String pimId, PimIdentifier... identifiers) {
        return new PimEntry(pimId, List.of(identifiers), "Brand", "Name", "Category", "Subcategory", true, null, null);
    }

    private PimIdentifier gtin(String value) {
        return new PimIdentifier(value, PimIdentifierType.GTIN);
    }

    private PimIdentifier mpn(String value) {
        return new PimIdentifier(value, PimIdentifierType.MPN);
    }
}
