package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class CodeJoinGuardTest {

    @Test
    void inheritedPairsOnlyRefuseLinks() {
        // given
        InventoryItem offer = offer("Acme", "G502", "5901234567801");

        // when
        CodeJoinGuard guard = MatchingRules.standard()
                .codeJoinGuard(SharedManufacturerCodes.of(List.of(offer)), pairOf(offer), VariantCodes.none());

        // then
        assertThat(guard.joinsOnlyByEan(offer)).isFalse();
        assertThat(guard.variantCodes().contains(offer.brandKey(), offer.mfn())).isTrue();
    }

    @Test
    void flaggedPairJoinsOnlyByEanAndRefusesLinks() {
        // given
        InventoryItem offer = offer("Acme", "G502", "5901234567801");

        // when
        CodeJoinGuard guard = MatchingRules.standard()
                .codeJoinGuard(SharedManufacturerCodes.of(List.of(offer)), VariantCodes.none(), pairOf(offer));

        // then
        assertThat(guard.joinsOnlyByEan(offer)).isTrue();
        assertThat(guard.variantCodes().contains(offer.brandKey(), offer.mfn())).isTrue();
    }

    @Test
    void offerUnderASharedCodeJoinsOnlyByEan() {
        // given
        InventoryItem first = offer("Acme", "BRAK", "5901234567801");
        InventoryItem second = offer("Acme", "BRAK", "5901234567802");
        InventoryItem otherSupplier = offer("Kosatec", "BRAK", "5901234567803");
        List<InventoryItem> offers = List.of(first, second, otherSupplier);

        // when
        CodeJoinGuard guard = MatchingRules.standard()
                .codeJoinGuard(SharedManufacturerCodes.of(offers), VariantCodes.none(), VariantCodes.none());

        // then
        assertThat(guard.joinsOnlyByEan(first)).isTrue();
        assertThat(guard.joinsOnlyByEan(second)).isTrue();
        assertThat(guard.joinsOnlyByEan(otherSupplier)).isFalse();
    }

    @Test
    void emptyPairsNeverAskForTheBrandKey() {
        // given
        InventoryItem spyOffer = spy(offer("Acme", "G502", "5901234567801"));

        // when
        boolean joinsOnlyByEan = new EanOnlyPairsGuard(VariantCodes.none()).joinsOnlyByEan(spyOffer);

        // then
        assertThat(joinsOnlyByEan).isFalse();
        verify(spyOffer, never()).brandKey();
    }

    @Test
    void pairsAreUnitedFromEveryGuard() {
        // given
        InventoryItem sharedA1 = offer("Acme", "A", "5901234567801");
        InventoryItem sharedA2 = offer("Acme", "A", "5901234567802");
        InventoryItem inheritedB = offer("Acme", "B", "5901234567803");
        InventoryItem flaggedC = offer("Acme", "C", "5901234567804");

        // when
        CodeJoinGuard guard = MatchingRules.standard().codeJoinGuard(
                SharedManufacturerCodes.of(List.of(sharedA1, sharedA2)), pairOf(inheritedB), pairOf(flaggedC));

        // then
        assertThat(guard.variantCodes().size()).isEqualTo(3);
        assertThat(guard.variantCodes().contains(sharedA1.brandKey(), sharedA1.mfn())).isTrue();
        assertThat(guard.variantCodes().contains(inheritedB.brandKey(), inheritedB.mfn())).isTrue();
        assertThat(guard.variantCodes().contains(flaggedC.brandKey(), flaggedC.mfn())).isTrue();
    }

    private static VariantCodes pairOf(InventoryItem offer) {
        return VariantCodes.of(Set.of(new VariantCodes.BrandCode(offer.brandKey(), offer.mfn())));
    }

    private static InventoryItem offer(String supplier, String mfn, String ean) {
        return new InventoryItem("Brand", null, ean, mfn, supplier, 10.0, "PLN", 1, 1, true, false, false);
    }
}
