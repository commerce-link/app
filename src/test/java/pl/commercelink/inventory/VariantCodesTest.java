package pl.commercelink.inventory;

import org.junit.jupiter.api.Test;
import pl.commercelink.inventory.supplier.InventoryItem;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VariantCodesTest {

    @Test
    void pairListedUnderTwoEansByOneSupplierIsAVariantCode() {
        // given
        InventoryItem first = offer("X", "Nike", "AIR1", "5901234567801");
        InventoryItem second = offer("X", "Nike", "AIR1", "5901234567802");
        InventoryItem offerOfY = offer("Y", "nike ", "AIR1", "5901234567803");

        // when
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(first, second, offerOfY)).variantCodes();

        // then
        assertThat(variantCodes.contains(offerOfY.brandKey(), offerOfY.mfn())).isTrue();
        assertThat(variantCodes.contains("nike", "AIR1")).isTrue();
        assertThat(variantCodes.size()).isEqualTo(1);
    }

    @Test
    void pairUnderOneEanAtEverySupplierIsNotAVariantCode() {
        // given
        InventoryItem offerOfX = offer("X", "Nike", "AIR1", "5901234567801");
        InventoryItem offerOfY = offer("Y", "Nike", "AIR1", "5901234567802");

        // when
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(offerOfX, offerOfY)).variantCodes();

        // then
        assertThat(variantCodes.contains(offerOfX.brandKey(), offerOfX.mfn())).isFalse();
        assertThat(variantCodes.contains(offerOfY.brandKey(), offerOfY.mfn())).isFalse();
        assertThat(variantCodes.size()).isZero();
    }

    @Test
    void sameCodeOfAnotherBrandIsNotAVariantCode() {
        // given
        InventoryItem first = offer("X", "Nike", "AIR1", "5901234567801");
        InventoryItem second = offer("X", "Nike", "AIR1", "5901234567802");
        InventoryItem otherBrand = offer("Y", "Adidas", "AIR1", "5901234567803");

        // when
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(first, second, otherBrand)).variantCodes();

        // then
        assertThat(variantCodes.contains(otherBrand.brandKey(), otherBrand.mfn())).isFalse();
    }

    @Test
    void plusUnitesBothSets() {
        // given
        VariantCodes nike = SharedManufacturerCodes.of(List.of(
                offer("X", "Nike", "AIR1", "5901234567801"),
                offer("X", "Nike", "AIR1", "5901234567802"))).variantCodes();
        VariantCodes adidas = SharedManufacturerCodes.of(List.of(
                offer("Y", "Adidas", "SAMBA", "5901234567803"),
                offer("Y", "Adidas", "SAMBA", "5901234567804"))).variantCodes();

        // when
        VariantCodes union = nike.plus(adidas);

        // then
        assertThat(union.contains("nike", "AIR1")).isTrue();
        assertThat(union.contains("adidas", "SAMBA")).isTrue();
        assertThat(union.size()).isEqualTo(2);
        assertThat(nike.plus(VariantCodes.none())).isSameAs(nike);
        assertThat(VariantCodes.none().plus(adidas)).isSameAs(adidas);
    }

    @Test
    void noneContainsNothing() {
        // given
        VariantCodes none = VariantCodes.none();

        // then
        assertThat(none.size()).isZero();
        assertThat(none.contains("nike", "AIR1")).isFalse();
    }

    @Test
    void offerWithoutBrandOrCodeNeverThrows() {
        // given
        InventoryItem noBrand = offer("X", null, "AIR1", "5901234567801");
        InventoryItem noCode = offer("X", "Nike", null, "5901234567802");
        VariantCodes variantCodes = SharedManufacturerCodes.of(List.of(
                offer("X", "Nike", "AIR1", "5901234567803"),
                offer("X", "Nike", "AIR1", "5901234567804"),
                noBrand, noCode)).variantCodes();

        // then
        assertThat(variantCodes.contains(noBrand.brandKey(), noBrand.mfn())).isFalse();
        assertThat(variantCodes.contains(noCode.brandKey(), noCode.mfn())).isFalse();
        assertThat(variantCodes.contains(null, null)).isFalse();
        assertThat(VariantCodes.none().contains(noBrand.brandKey(), noBrand.mfn())).isFalse();
    }

    @Test
    void pairWithoutBrandOrCodeIsNeverAVariantCode() {
        // given
        InventoryItem noCodeFirst = offer("X", "Nike", null, "5901234567801");
        InventoryItem noCodeSecond = offer("X", "Nike", null, "5901234567802");
        InventoryItem noBrandFirst = offer("X", null, "AIR1", "5901234567803");
        InventoryItem noBrandSecond = offer("X", null, "AIR1", "5901234567804");
        InventoryItem noCodeOfY = offer("Y", "Nike", null, "5901234567805");
        InventoryItem noBrandOfY = offer("Y", null, "AIR1", "5901234567806");

        // when
        SharedManufacturerCodes sharedCodes = SharedManufacturerCodes.of(
                List.of(noCodeFirst, noCodeSecond, noBrandFirst, noBrandSecond, noCodeOfY, noBrandOfY));
        VariantCodes variantCodes = sharedCodes.variantCodes();

        // then
        assertThat(variantCodes.contains(noCodeOfY.brandKey(), noCodeOfY.mfn())).isFalse();
        assertThat(variantCodes.contains(noBrandOfY.brandKey(), noBrandOfY.mfn())).isFalse();
        assertThat(variantCodes.contains("nike", null)).isFalse();
        assertThat(variantCodes.contains(null, "AIR1")).isFalse();
        assertThat(variantCodes.size()).isZero();
        assertThat(sharedCodes.isShared(noCodeFirst)).isTrue();
        assertThat(sharedCodes.isShared(noBrandFirst)).isTrue();
    }

    @Test
    void containsIsFalseForMissingBrandOrCodeWhateverTheSetHolds() {
        // given
        VariantCodes variantCodes = VariantCodes.of(Set.of(
                new VariantCodes.BrandCode(null, "AIR1"), new VariantCodes.BrandCode("nike", null)));

        // then
        assertThat(variantCodes.contains(null, "AIR1")).isFalse();
        assertThat(variantCodes.contains("nike", null)).isFalse();
    }

    private static InventoryItem offer(String supplier, String brand, String mfn, String ean) {
        return new InventoryItem(brand, null, ean, mfn, supplier, 10.0, "PLN", 1, 1, true, false, false);
    }
}
