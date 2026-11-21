package pl.commercelink.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.commercelink.inventory.supplier.InventoryItem;
import pl.commercelink.inventory.supplier.SupplierRegistry;
import pl.commercelink.pim.api.PimCatalog;
import pl.commercelink.pim.api.PimView;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MatchingRulesTest {

    @Mock
    private PimCatalog pimCatalog;
    @Mock
    private PimView pim;
    @Mock
    private SupplierRegistry supplierRegistry;

    private final AtomicInteger witnessCalls = new AtomicInteger();
    private final Supplier<Collection<InventoryItem>> countingWitnesses = () -> {
        witnessCalls.incrementAndGet();
        return List.of();
    };

    @BeforeEach
    void emptyPim() {
        lenient().when(pim.findAll()).thenReturn(List.of());
    }

    @Test
    void standardRulesListTheConflictingOfferFilter() {
        // when / then
        assertThat(MatchingRules.standard().offerFilters()).hasSize(1).first().isInstanceOf(ConflictingOfferFilter.class);
    }

    @Test
    void conflictingOfferFilterIsNotListedWhenSwitchedOff() {
        // when / then
        assertThat(MatchingRules.of(new InventoryMatchingProperties(false, true, true, true, true, true)).offerFilters()).isEmpty();
    }

    @Test
    void offerFiltersDoNotFetchWitnessesForAnEmptyPool() {
        // when
        autoDiscovery(InventoryMatchingProperties.allEnabled())
                .group(List.of(), pim, countingWitnesses, VariantCodes.none(), VariantCodes.none());

        // then
        assertThat(witnessCalls).hasValue(0);
    }

    @Test
    void witnessesAreNotFetchedWhenNoFilterIsListed() {
        // when
        autoDiscovery(new InventoryMatchingProperties(false, true, true, true, true, true))
                .group(List.of(item("EAN1", "MFN1", "A")), pim, countingWitnesses, VariantCodes.none(), VariantCodes.none());

        // then
        assertThat(witnessCalls).hasValue(0);
    }

    @Test
    void witnessesAreFetchedOnceForANonEmptyPool() {
        // when
        autoDiscovery(InventoryMatchingProperties.allEnabled())
                .group(List.of(item("EAN1", "MFN1", "A")), pim, countingWitnesses, VariantCodes.none(), VariantCodes.none());

        // then
        assertThat(witnessCalls).hasValue(1);
    }

    @Test
    void standardRulesListBothGroupRefinersInOrder() {
        // when / then
        assertThat(MatchingRules.standard().groupRefiners()).extracting(Object::getClass)
                .containsExactly(SingleCodePriceSplit.class, MixedCodePriceControl.class);
    }

    @Test
    void singleCodeSplitIsNotListedWhenSwitchedOff() {
        // when / then
        assertThat(MatchingRules.of(new InventoryMatchingProperties(true, true, false, true, true, true)).groupRefiners())
                .extracting(Object::getClass).containsExactly(MixedCodePriceControl.class);
    }

    @Test
    void groupLeftAloneByEveryRefinerIsTheSameObject() {
        // given
        List<InventoryItem> offers = List.of(item("EAN1", "MFN1", "A"), item("EAN1", "MFN2", "B"));
        MatchedInventory group = new MatchedInventory(
                new InventoryKey(Set.of("EAN1"), Set.of("MFN1", "MFN2")), offers, supplierRegistry);

        // when
        List<MatchedInventory> result = autoDiscovery(InventoryMatchingProperties.allEnabled())
                .processMatchedInventoryCandidates(group);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.getFirst()).isSameAs(group);
    }

    @Test
    void standardRulesListTheLinkVetosInOrder() {
        // given
        MatchingRules rules = MatchingRules.standard();

        // when
        List<LinkVeto> vetos = rules.linkVetos();

        // then
        assertThat(vetos).extracting(Object::getClass).containsExactly(
                ConflictingPimIdsVeto.class, TargetSharesEanVeto.class, TargetHasOwnGroupVeto.class, PriceTooFarVeto.class);
    }

    @Test
    void linkingIsOffWhenSwitchedOff() {
        // given
        InventoryMatchingProperties properties = new InventoryMatchingProperties(true, true, true, false, true, true);

        // when
        boolean links = MatchingRules.of(properties).linksOwnToGlobal();

        // then
        assertThat(links).isFalse();
    }

    private InventoryAutoDiscovery autoDiscovery(InventoryMatchingProperties properties) {
        return new InventoryAutoDiscovery(pimCatalog, supplierRegistry, properties);
    }

    private InventoryItem item(String ean, String mfn, String supplier) {
        return new InventoryItem("Brand", null, ean, mfn, supplier, 100, "PLN", 5, 1, true, false, false);
    }
}
