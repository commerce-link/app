package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.OwnToGlobalLinks.Decision;
import pl.commercelink.inventory.OwnToGlobalLinks.Outcome;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimView;
import pl.commercelink.products.brand.NoNameBrand;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnToGlobalLinksTest {

    private static final String GLOBAL_EAN = "8720689010825";
    private static final String OWN_EAN = "8720689021180";
    private static final String CODE = "QP1324/20";
    private static final String PHILIPS = "Philips";

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;
    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimView pimView;

    @Test
    void ownGroupIsLinkedToTheOnlyGlobalGroupOfItsBrandAndCode() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.LINKED);
            assertThat(decision.own()).isSameAs(own);
            assertThat(decision.global()).isSameAs(global);
            assertThat(decision.ownAveragePrice()).isCloseTo(70.38, within(0.005));
            assertThat(decision.globalAveragePrice()).isCloseTo(107.11, within(0.005));
        });
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactlyInAnyOrderElementsOf(global.getEans());
    }

    @Test
    void ownGroupSharingAnEanWithAnyGlobalGroupIsNotACandidate() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory other = group(offer("5900000000017", "OTHER-1", 40.0, "Asbis", "Other"));
        MatchedInventory own = group(offer(OWN_EAN, CODE, 75.00, "Action", PHILIPS),
                offer("5900000000017", CODE, 65.76, "Elko", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global, other), VariantCodes.none());

        // then
        assertThat(decisions).isEmpty();
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void offersWithoutAMatchableBrandOrCodeMakeNoPair() {
        // given
        MatchedInventory global = group(offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", null),
                offer(GLOBAL_EAN, CODE, 107.11, "Morele", NoNameBrand.NAME),
                offer(GLOBAL_EAN, null, 107.11, "Wave", PHILIPS));
        MatchedInventory withoutBrand = group(offer("5900000000024", CODE, 75.00, "Action", null));
        MatchedInventory noName = group(offer("5900000000031", CODE, 75.00, "Action", NoNameBrand.NAME));
        MatchedInventory withoutCode = group(offer("5900000000048", null, 75.00, "Action", PHILIPS));
        List<MatchedInventory> owns = List.of(withoutBrand, noName, withoutCode);

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(owns), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).isEmpty();
        assertThat(owns).allSatisfy(own -> assertThat(own.getInventoryKey().getLinkedEans()).isEmpty());
    }

    @Test
    void sameCodeOfAnotherBrandMakesNoPair() {
        // given
        MatchedInventory global = group(offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", "Saeco"));
        MatchedInventory own = philipsOwn();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).isEmpty();
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void variantCodeIsNeverLinked() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(
                offer("5900000000055", CODE, 100.0, "Wave", PHILIPS),
                offer("5900000000062", CODE, 100.0, "Wave", PHILIPS))).variantCodes();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), variantCodes);

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.VARIANT_CODE);
            assertThat(decision.global()).isNull();
        });
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void twoGlobalGroupsOfTheCodeLeaveTheOwnGroupAlone() {
        // given
        MatchedInventory first = philipsGlobal();
        MatchedInventory second = group(offer("8720689010832", CODE, 110.00, "Morele", PHILIPS));
        MatchedInventory own = philipsOwn();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(first, second), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.SEVERAL_TARGETS);
            assertThat(decision.global()).isNull();
        });
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void differentPimIdsAreNotLinked() {
        // given
        MatchedInventory global = withPimId("pim-global", GLOBAL_EAN, offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", PHILIPS));
        MatchedInventory own = withPimId("pim-store", OWN_EAN, offer(OWN_EAN, CODE, 75.00, "Action", PHILIPS),
                offer(OWN_EAN, CODE, 65.76, "Elko", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.CONFLICTING_PIM_IDS);
            assertThat(decision.global()).isSameAs(global);
        });
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void pimIdOnOneSideOnlyIsLinked() {
        // given
        MatchedInventory global = withPimId("pim-global", GLOBAL_EAN, offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", PHILIPS));
        MatchedInventory own = philipsOwn();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> assertThat(decision.outcome()).isEqualTo(Outcome.LINKED));
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN);
    }

    @Test
    void targetWhoseEanIsAlsoInAnotherGlobalGroupIsNotLinked() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory sibling = withPimId("pim-sibling", GLOBAL_EAN, offer(GLOBAL_EAN, "QP1324/30", 120.00, "Wave", PHILIPS));
        MatchedInventory own = philipsOwn();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global, sibling), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.TARGET_SHARES_EAN);
            assertThat(decision.global()).isSameAs(global);
        });
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void targetThatAlreadyHasAnOwnGroupByEanIsNotLinked() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        MatchedInventory ownCopyOfGlobal = group(offer(GLOBAL_EAN, "QP-OTHER", 100.00, "Komputronik", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own, ownCopyOfGlobal)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.own()).isSameAs(own);
            assertThat(decision.outcome()).isEqualTo(Outcome.TARGET_HAS_OWN_GROUP);
        });
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void pricesMoreThanTwiceApartAreNotLinked() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = group(offer(OWN_EAN, CODE, 50.00, "Action", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.PRICE_TOO_FAR);
            assertThat(decision.ownAveragePrice()).isCloseTo(50.00, within(0.005));
            assertThat(decision.globalAveragePrice()).isCloseTo(107.11, within(0.005));
        });
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void pricesWithinTheRatioAreLinked() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = group(offer(OWN_EAN, CODE, 56.50, "Action", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> assertThat(decision.outcome()).isEqualTo(Outcome.LINKED));
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN);
    }

    @Test
    void priceComparesOnlyOffersOfTheSharedPair() {
        // given
        MatchedInventory global = group(offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", PHILIPS),
                offer(GLOBAL_EAN, "QP1324/99", 1000.00, "Wave", PHILIPS));
        MatchedInventory own = philipsOwn();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.LINKED);
            assertThat(decision.globalAveragePrice()).isCloseTo(107.11, within(0.005));
        });
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN);
    }

    @Test
    void twoOwnGroupsOfOneGlobalGroupAreBothLeftAlone() {
        // given
        MatchedInventory global = group(offer(GLOBAL_EAN, "A", 100.00, "MxSolution", PHILIPS),
                offer(GLOBAL_EAN, "B", 100.00, "Wave", PHILIPS));
        MatchedInventory first = group(offer(OWN_EAN, "A", 90.00, "Action", PHILIPS));
        MatchedInventory second = group(offer("8720689021197", "B", 95.00, "Elko", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(first, second)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).hasSize(2).allSatisfy(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.SEVERAL_OWN_GROUPS);
            assertThat(decision.global()).isSameAs(global);
        });
        assertThat(first.getInventoryKey().getLinkedEans()).isEmpty();
        assertThat(second.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void emptyPoolsGiveNoDecisions() {
        // given
        MatchedInventory own = philipsOwn();

        // when
        List<Decision> withoutOwnGroups = OwnToGlobalLinks.apply(InventoryIndex.of(List.of()), generation(philipsGlobal()), VariantCodes.none());
        List<Decision> withoutGlobalGroups = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(), VariantCodes.none());

        // then
        assertThat(withoutOwnGroups).isEmpty();
        assertThat(withoutGlobalGroups).isEmpty();
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void conflictingOfferRejectedGloballyMakesNoLink() {
        // given: Elko lists the code of the 7600 under the EAN of the 9060; both sides are confirmed by two suppliers,
        // so rule A leaves that offer out of the global pool
        when(pimView.findAll()).thenReturn(List.of());
        String ean7600 = "4895106293618";
        String ean9060 = "4895106296381";
        List<InventoryItem> globalOffers = List.of(
                offer(ean7600, "11324-01-20G", 1843, "Kosatec", "Sapphire"),
                offer(ean7600, "11324-01-20G", 1810, "Morele", "Sapphire"),
                offer(ean9060, "11350-03-20G", 2613, "Kosatec", "Sapphire"),
                offer(ean9060, "11350-03-20G", 2663, "IncomGroup", "Sapphire"),
                offer(ean9060, "11324-01-20G", 3563, "Elko", "Sapphire"));
        InventoryAutoDiscovery autoDiscovery = new InventoryAutoDiscovery(pimCatalog, taxonomyCache, supplierRegistry,
                InventoryMatchingProperties.allEnabled());
        InventoryAutoDiscovery.Grouping globalGrouping = autoDiscovery.group(globalOffers, pimView, List::of, VariantCodes.none());
        MatchedInventory own = group(offer("4895106299993", "11324-01-20G", 1850, "Action", "Sapphire"));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)),
                generation(globalGrouping.groups()), globalGrouping.variantCodes());

        // then
        assertThat(globalGrouping.groups()).flatExtracting(MatchedInventory::getInventoryItems)
                .noneMatch(offer -> "Elko".equals(offer.supplier()));
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.LINKED);
            assertThat(decision.global().getEans()).containsExactly(ean7600);
        });
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(ean7600).doesNotContain(ean9060);
    }

    @Test
    void conflictingPimIdsComeBeforeAPriceTooFar() {
        // given
        MatchedInventory global = withPimId("pim-global", GLOBAL_EAN, offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", PHILIPS));
        MatchedInventory own = withPimId("pim-store", OWN_EAN, offer(OWN_EAN, CODE, 50.00, "Action", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision ->
                assertThat(decision.outcome()).isEqualTo(Outcome.CONFLICTING_PIM_IDS));
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void targetSharingAnEanComesBeforeATargetWithAnOwnGroup() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory sibling = withPimId("pim-sibling", GLOBAL_EAN, offer(GLOBAL_EAN, "QP1324/30", 120.00, "Wave", PHILIPS));
        MatchedInventory own = philipsOwn();
        MatchedInventory ownCopyOfGlobal = group(offer(GLOBAL_EAN, "QP-OTHER", 100.00, "Komputronik", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own, ownCopyOfGlobal)),
                generation(global, sibling), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision ->
                assertThat(decision.outcome()).isEqualTo(Outcome.TARGET_SHARES_EAN));
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void targetWithAnOwnGroupComesBeforeAPriceTooFar() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = group(offer(OWN_EAN, CODE, 50.00, "Action", PHILIPS));
        MatchedInventory ownCopyOfGlobal = group(offer(GLOBAL_EAN, "QP-OTHER", 100.00, "Komputronik", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own, ownCopyOfGlobal)),
                generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision ->
                assertThat(decision.outcome()).isEqualTo(Outcome.TARGET_HAS_OWN_GROUP));
        assertThat(own.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void protectedPairIsIgnoredWhenAnotherPairIsShared() {
        // given: the group also sells a variant code, priced far from the shared one and carried by another global group
        // and by the target itself, whose offer under it would pull the target's average far from 107.11
        String variantCode = "QP-VAR";
        MatchedInventory global = group(offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", PHILIPS),
                offer(GLOBAL_EAN, variantCode, 900.00, "Wave", PHILIPS));
        MatchedInventory variantTarget = group(offer("8720689010849", variantCode, 480.00, "Wave", PHILIPS));
        MatchedInventory own = group(offer(OWN_EAN, CODE, 75.00, "Action", PHILIPS), offer(OWN_EAN, CODE, 65.76, "Elko", PHILIPS),
                offer(OWN_EAN, variantCode, 500.00, "Komputronik", PHILIPS));
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(
                offer("5900000000079", variantCode, 480.00, "Wave", PHILIPS),
                offer("5900000000086", variantCode, 480.00, "Wave", PHILIPS))).variantCodes();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)),
                generation(global, variantTarget), variantCodes);

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.LINKED);
            assertThat(decision.global()).isSameAs(global);
            assertThat(decision.ownAveragePrice()).isCloseTo(70.38, within(0.005));
            assertThat(decision.globalAveragePrice()).isCloseTo(107.11, within(0.005));
        });
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN);
    }

    @Test
    void sameCodeOfTwoBrandsLinksOnlyTheOwnGroupOfTheMatchingBrand() {
        // given: the Philips group is collected first, the global offer is of Philips
        MatchedInventory global = philipsGlobal();
        MatchedInventory philips = philipsOwn();
        MatchedInventory saeco = group(offer("8720689021203", CODE, 70.00, "Action", "Saeco"));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(philips, saeco)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.own()).isSameAs(philips);
            assertThat(decision.outcome()).isEqualTo(Outcome.LINKED);
        });
        assertThat(philips.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN);
        assertThat(saeco.getInventoryKey().getLinkedEans()).isEmpty();
    }

    @Test
    void pairRepeatedInTheOwnGroupCountsTheGlobalOfferOnce() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = group(offer(OWN_EAN, CODE, 75.00, "Action", PHILIPS), offer(OWN_EAN, CODE, 65.76, "Elko", PHILIPS),
                offer(OWN_EAN, CODE, 70.38, "Komputronik", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());

        // then
        assertThat(decisions).singleElement().satisfies(decision -> {
            assertThat(decision.outcome()).isEqualTo(Outcome.LINKED);
            assertThat(decision.ownAveragePrice()).isCloseTo(70.38, within(0.005));
            assertThat(decision.globalAveragePrice()).isCloseTo(107.11, within(0.005));
        });
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactly(GLOBAL_EAN);
    }

    private MatchedInventory philipsGlobal() {
        return group(offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", PHILIPS));
    }

    private MatchedInventory philipsOwn() {
        return group(offer(OWN_EAN, CODE, 75.00, "Action", PHILIPS), offer(OWN_EAN, CODE, 65.76, "Elko", PHILIPS));
    }

    private MatchedInventory group(InventoryItem... offers) {
        InventoryKey key = new InventoryKey();
        for (InventoryItem offer : offers) {
            key.addEan(offer.ean());
            if (offer.mfn() != null) {
                key.addManufacturerCode(offer.mfn());
            }
        }
        return new MatchedInventory(key, List.of(offers), taxonomyCache, supplierRegistry);
    }

    private MatchedInventory withPimId(String pimId, String ean, InventoryItem... offers) {
        InventoryKey key = InventoryKey.fromPimIdAndEan(pimId, ean);
        return new MatchedInventory(key, List.of(offers), taxonomyCache, supplierRegistry);
    }

    private static GlobalMatchedInventory.Generation generation(MatchedInventory... groups) {
        return generation(List.of(groups));
    }

    private static GlobalMatchedInventory.Generation generation(Collection<MatchedInventory> groups) {
        List<MatchedInventory> all = new ArrayList<>(groups);
        return new GlobalMatchedInventory.Generation(1, all, InventoryIndex.of(all), VariantCodes.none());
    }

    private static InventoryItem offer(String ean, String mfn, double netPrice, String supplier, String brand) {
        return new InventoryItem(brand, null, ean, mfn, supplier, netPrice, "PLN", 5, 1, true, false, false);
    }
}
