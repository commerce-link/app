package pl.commercelink.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.commercelink.products.Product;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryKeyTest {

    @Test
    void mergeAdoptsIdWhenAbsent() {
        // given
        InventoryKey key = new InventoryKey("5901234567890", "MFN-1");

        // when
        key.merge(new InventoryKey("PIM-1"));

        // then
        assertThat(key.getId()).isEqualTo("PIM-1");
    }

    @Test
    void mergeDoesNotOverwriteExistingId() {
        // given
        InventoryKey key = new InventoryKey("PIM-1");

        // when
        key.merge(new InventoryKey("PIM-2"));

        // then
        assertThat(key.getId()).isEqualTo("PIM-1");
    }

    @Test
    void mergeUnionsEansAndProductCodes() {
        // given
        InventoryKey key = new InventoryKey("5901234567890", "MFN-1");

        // when
        key.merge(new InventoryKey("4000000000009", "MFN-2"));

        // then
        assertThat(key.matches(new InventoryKey("4000000000009", null))).isTrue();
        assertThat(key.getProductCodes()).containsExactlyInAnyOrder("MFN-1", "MFN-2");
        assertThat(key.matches(new InventoryKey("5901234567890", null))).isTrue();
    }

    @Test
    void sameCodeWithAnotherEanIsAnotherProduct() {
        // given
        InventoryKey size42 = new InventoryKey("5901000000042", "SHOE-1");

        // when / then
        assertThat(size42.matches(new InventoryKey("5901000000043", "SHOE-1"))).isFalse();
    }

    @Test
    void sameEanOrPimIdIsTheSameProduct() {
        // given
        InventoryKey key = InventoryKey.fromPimIdAndEan("P1", "5901000000001");

        // when / then
        assertThat(key.matches(new InventoryKey("5901000000001", "OTHER-1"))).isTrue();
        assertThat(key.matches(InventoryKey.fromPimIdAndEan("P1", "5903000000003"))).isTrue();
    }

    @Test
    void productKeyCarriesItsPimId() {
        // given
        Product product = new Product("cat-1", "P1", "5901234567890", "MFN-1", "Acme", null, "Product", null);

        // when
        InventoryKey key = InventoryKey.fromProduct(product);

        // then
        assertThat(key.getId()).isEqualTo("P1");
    }

    private static final String GLOBAL_EAN = "8720689010825";
    private static final String OWN_EAN = "8720689021180";

    private static InventoryKey linkedOwnKey() {
        InventoryKey key = new InventoryKey(OWN_EAN, "QP1324/20");
        key.linkTo(List.of(GLOBAL_EAN));
        return key;
    }

    @Test
    void linkIsNotSerializedWithTheKey() throws Exception {
        // given
        InventoryKey key = linkedOwnKey();

        // when
        String json = new ObjectMapper().writeValueAsString(key);

        // then
        assertThat(json).contains(OWN_EAN).doesNotContain("linkedEans").doesNotContain(GLOBAL_EAN);
    }

    @Test
    void copyAndMergeCarryTheLink() {
        // given
        InventoryKey linked = linkedOwnKey();
        InventoryKey row = InventoryKey.fromEan("5900000000001");

        // when
        InventoryKey copy = linked.copy();
        row.merge(linked);

        // then
        assertThat(copy.getLinkedEans()).containsExactly(GLOBAL_EAN);
        assertThat(row.getLinkedEans()).containsExactly(GLOBAL_EAN);
    }

    @Test
    void isLinkedWithWorksInBothDirections() {
        // given
        InventoryKey own = linkedOwnKey();
        InventoryKey global = new InventoryKey(GLOBAL_EAN, "QP1324/20");

        // when / then
        assertThat(own.isLinkedWith(global)).isTrue();
        assertThat(global.isLinkedWith(own)).isTrue();
        assertThat(own.isLinkedWith(InventoryKey.fromEan("5900000000001"))).isFalse();
    }

    @Test
    void keysSharingOnlyAnEanAreNotLinked() {
        // given
        InventoryKey first = new InventoryKey(OWN_EAN, "QP1324/20");
        InventoryKey second = new InventoryKey(OWN_EAN, "QP1324/20");

        // when / then
        assertThat(first.isLinkedWith(second)).isFalse();
        assertThat(first.getLinkedEans()).isEmpty();
    }
}
