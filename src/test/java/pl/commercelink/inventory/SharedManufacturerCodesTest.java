package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharedManufacturerCodesTest {

    @Test
    void listsTheOffersUnderACodeTheirSupplierGaveToSeveralEans() {
        // given
        InventoryItem doll = offer("Acme", "BRAK", "5901234567801");
        InventoryItem car = offer("Acme", "BRAK", "5901234567802");
        InventoryItem pool = offer("Kosatec", "BRAK", "5901234567803");
        InventoryItem mouse = offer("Acme", "G502", "5901234567804");

        // when
        SharedManufacturerCodes sharedCodes = SharedManufacturerCodes.of(List.of(doll, car, pool, mouse));

        // then
        assertThat(sharedCodes.offersUnderSharedCode()).containsExactlyInAnyOrder(doll, car);
    }

    @Test
    void isSharedStaysPerSupplierWhileVariantCodesSpanSuppliers() {
        // given
        InventoryItem first = new InventoryItem("Nike", null, "5901234567801", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false);
        InventoryItem second = new InventoryItem("Nike", null, "5901234567802", "AIR1", "X", 10.0, "PLN", 1, 1, true, false, false);
        InventoryItem offerOfY = new InventoryItem("nike ", null, "5901234567803", "AIR1", "Y", 10.0, "PLN", 1, 1, true, false, false);

        // when
        SharedManufacturerCodes sharedCodes = SharedManufacturerCodes.of(List.of(first, second, offerOfY));

        // then
        assertThat(sharedCodes.isShared(offerOfY)).isFalse();
        assertThat(sharedCodes.isShared(first)).isTrue();
        assertThat(sharedCodes.variantCodes().contains(offerOfY.brandKey(), offerOfY.mfn())).isTrue();
    }

    private static InventoryItem offer(String supplier, String mfn, String ean) {
        return new InventoryItem("Brand", null, ean, mfn, supplier, 10.0, "PLN", 1, 1, true, false, false);
    }
}
