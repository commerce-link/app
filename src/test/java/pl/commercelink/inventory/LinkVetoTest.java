package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.OwnToGlobalLinks.Decision;
import pl.commercelink.inventory.OwnToGlobalLinks.Outcome;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.taxonomy.TaxonomyCache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class LinkVetoTest {

    private static final String GLOBAL_EAN = "8720689010825";
    private static final String OWN_EAN = "8720689021180";
    private static final String CODE = "QP1324/20";
    private static final String PHILIPS = "Philips";

    @Mock
    private TaxonomyCache taxonomyCache;
    @Mock
    private SupplierRegistry supplierRegistry;

    @Test
    void firstVetoInOrderNamesTheOutcome() {
        // given: both pim ids conflict and the prices are 5x apart
        MatchedInventory global = withPimId("B", GLOBAL_EAN, offer(GLOBAL_EAN, CODE, 500.00, "MxSolution", PHILIPS));
        MatchedInventory own = withPimId("A", OWN_EAN, offer(OWN_EAN, CODE, 100.00, "Action", PHILIPS));
        MatchedInventory global2 = withPimId("B", GLOBAL_EAN, offer(GLOBAL_EAN, CODE, 500.00, "MxSolution", PHILIPS));
        MatchedInventory own2 = withPimId("A", OWN_EAN, offer(OWN_EAN, CODE, 100.00, "Action", PHILIPS));

        // when
        List<Decision> standard = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none());
        List<Decision> reversed = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own2)), generation(global2), VariantCodes.none(),
                List.of(new PriceTooFarVeto(), new ConflictingPimIdsVeto()));

        // then
        assertThat(standard).singleElement().extracting(Decision::outcome).isEqualTo(Outcome.CONFLICTING_PIM_IDS);
        assertThat(reversed).singleElement().extracting(Decision::outcome).isEqualTo(Outcome.PRICE_TOO_FAR);
    }

    @Test
    void noVetoLinksTheGroups() {
        // given
        MatchedInventory global = withPimId("B", GLOBAL_EAN, offer(GLOBAL_EAN, CODE, 500.00, "MxSolution", PHILIPS));
        MatchedInventory own = withPimId("A", OWN_EAN, offer(OWN_EAN, CODE, 100.00, "Action", PHILIPS));

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), VariantCodes.none(), List.of());

        // then
        assertThat(decisions).singleElement().extracting(Decision::outcome).isEqualTo(Outcome.LINKED);
        assertThat(own.getInventoryKey().getLinkedEans()).containsExactlyInAnyOrderElementsOf(global.getEans());
    }

    @Test
    void variantCodeIsRefusedBeforeAnyVeto() {
        // given
        MatchedInventory global = philipsGlobal();
        MatchedInventory own = philipsOwn();
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(
                offer("5900000000055", CODE, 100.0, "Wave", PHILIPS),
                offer("5900000000062", CODE, 100.0, "Wave", PHILIPS))).variantCodes();

        // when
        List<Decision> decisions = OwnToGlobalLinks.apply(InventoryIndex.of(List.of(own)), generation(global), variantCodes, List.of());

        // then
        assertThat(decisions).singleElement().extracting(Decision::outcome).isEqualTo(Outcome.VARIANT_CODE);
    }

    @Test
    void eachVetoRefusesWithItsOwnOutcome() {
        // given
        MatchedInventory global = withPimId("B", GLOBAL_EAN, offer(GLOBAL_EAN, CODE, 107.11, "MxSolution", PHILIPS));
        MatchedInventory ownWithOtherPimId = withPimId("A", OWN_EAN, offer(OWN_EAN, CODE, 107.11, "Action", PHILIPS));
        LinkCandidate conflicting = candidate(ownWithOtherPimId, global, InventoryIndex.of(List.of(ownWithOtherPimId)), generation(global).index());

        MatchedInventory sharedGlobal = philipsGlobal();
        MatchedInventory sibling = withPimId("pim-sibling", GLOBAL_EAN, offer(GLOBAL_EAN, "QP1324/30", 120.00, "Wave", PHILIPS));
        MatchedInventory ownA = philipsOwn();
        LinkCandidate sharesEan = candidate(ownA, sharedGlobal, InventoryIndex.of(List.of(ownA)), generation(sharedGlobal, sibling).index());

        MatchedInventory targetGlobal = philipsGlobal();
        MatchedInventory ownB = philipsOwn();
        MatchedInventory ownCopyOfGlobal = group(offer(GLOBAL_EAN, "QP-OTHER", 100.00, "Komputronik", PHILIPS));
        LinkCandidate hasOwnGroup = candidate(ownB, targetGlobal, InventoryIndex.of(List.of(ownB, ownCopyOfGlobal)), generation(targetGlobal).index());

        MatchedInventory farGlobal = philipsGlobal();
        MatchedInventory ownC = philipsOwn();
        LinkCandidate priceTooFar = new LinkCandidate(ownC, farGlobal, 50.0, 107.11,
                InventoryIndex.of(List.of(ownC)), generation(farGlobal).index());
        LinkCandidate priceClose = new LinkCandidate(ownC, farGlobal, 100.0, 107.11,
                InventoryIndex.of(List.of(ownC)), generation(farGlobal).index());

        // when / then
        assertThat(new ConflictingPimIdsVeto().refuse(conflicting)).contains(Outcome.CONFLICTING_PIM_IDS);
        assertThat(new TargetSharesEanVeto().refuse(sharesEan)).contains(Outcome.TARGET_SHARES_EAN);
        assertThat(new TargetHasOwnGroupVeto().refuse(hasOwnGroup)).contains(Outcome.TARGET_HAS_OWN_GROUP);
        assertThat(new PriceTooFarVeto().refuse(priceTooFar)).contains(Outcome.PRICE_TOO_FAR);

        assertThat(new ConflictingPimIdsVeto().refuse(sharesEan)).isEqualTo(Optional.empty());
        assertThat(new TargetSharesEanVeto().refuse(hasOwnGroup)).isEqualTo(Optional.empty());
        assertThat(new TargetHasOwnGroupVeto().refuse(priceClose)).isEqualTo(Optional.empty());
        assertThat(new PriceTooFarVeto().refuse(priceClose)).isEqualTo(Optional.empty());
    }

    private static LinkCandidate candidate(MatchedInventory own, MatchedInventory global, InventoryIndex ownIndex, InventoryIndex globalIndex) {
        return new LinkCandidate(own, global, 100.0, 100.0, ownIndex, globalIndex);
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
