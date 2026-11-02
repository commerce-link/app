package pl.commercelink.inventory.supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryItemTest {

    @ParameterizedTest
    @ValueSource(strings = {"?", ".", " ? ", "0"})
    void placeholderCodeMakesTheOfferUnsellable(String placeholder) {
        // given
        InventoryItem item = new InventoryItem("1234567890123", placeholder, 10.0, "PLN", 5, 1, "Morele");

        // when
        boolean sellable = item.isSellable();

        // then
        assertFalse(item.hasMfn());
        assertFalse(sellable);
    }

    @Test
    void codeContainingADotIsARealCode() {
        // given
        InventoryItem item = new InventoryItem("1234567890123", "MFN.1", 10.0, "PLN", 5, 1, "Morele");

        // when
        boolean sellable = item.isSellable();

        // then
        assertTrue(item.hasMfn());
        assertTrue(sellable);
    }

    @Test
    void withSupplierReattributesTheRowKeepingEveryOtherField() {
        // given
        InventoryItem parsedByType = new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 1, "Kosatec",
                true, true, false, "0101");

        // when
        InventoryItem stamped = parsedByType.withSupplier("Kosatec-k7f3a9c2");

        // then
        assertEquals("Kosatec-k7f3a9c2", stamped.supplier());
        assertEquals("Kosatec-k7f3a9c2_1234567890123_MFN-1", stamped.uuid());
        assertEquals(parsedByType.netPrice(), stamped.netPrice());
        assertEquals(parsedByType.sku(), stamped.sku());
        assertSame(parsedByType, parsedByType.withSupplier("Kosatec"));
    }

    @Test
    void skuIsKeptWithoutNormalization() {
        InventoryItem item = new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 1, "Elko",
                true, false, false, "0101");
        assertEquals("0101", item.sku());
        assertEquals("1234567890123", item.ean());
    }

    @Test
    void delegatingConstructorsLeaveSkuNull() {
        assertNull(new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 1, "Elko").sku());
        assertNull(new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 1, "Elko", false).sku());
        assertNull(new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 1, "Elko", true, true, false).sku());
    }

    @Test
    void currencyConversionAndEanRewritePreserveSku() {
        InventoryItem item = new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 1, "Elko",
                true, false, false, "101");
        assertEquals("101", item.toLocalCurrency("EUR", 4.0).orElseThrow().sku());
        assertEquals("101", item.withEan("4006381333931").sku());
    }

    @Test
    void copiesKeepTheBrand() {
        // given
        InventoryItem item = new InventoryItem("ASUS", "101", "1234567890123", "MFN-1", "Elko",
                10.0, "PLN", 5, 1, true, false, false);

        // when
        InventoryItem otherSupplier = item.withSupplier("Elko-k7f3a9c2");
        InventoryItem otherEan = item.withEan("4006381333931");
        InventoryItem otherCurrency = item.toLocalCurrency("EUR", 4.0).orElseThrow();

        // then
        assertEquals("ASUS", otherSupplier.brand());
        assertEquals("ASUS", otherEan.brand());
        assertEquals("ASUS", otherCurrency.brand());
    }

    @Test
    void copiesChangeOnlyTheirOwnValue() {
        // given
        InventoryItem item = new InventoryItem("ASUS", "101", "1234567890123", "MFN-1", "Elko",
                10.0, "PLN", 5, 1, true, false, true);

        // when / then
        assertEquals(new InventoryItem("ASUS", "101", "1234567890123", "MFN-1", "Elko-k7f3a9c2",
                10.0, "PLN", 5, 1, true, false, true), item.withSupplier("Elko-k7f3a9c2"));
        assertEquals(new InventoryItem("ASUS", "101", "4006381333931", "MFN-1", "Elko",
                10.0, "PLN", 5, 1, true, false, true), item.withEan("4006381333931"));
        assertEquals(new InventoryItem("ASUS", "101", "1234567890123", "MFN-1", "Elko",
                40.0, "EUR", 5, 1, true, false, true), item.toLocalCurrency("EUR", 4.0).orElseThrow());
    }

    @Test
    void oldConstructorsKeepTheirParameterOrder() {
        // when / then
        assertEquals(new InventoryItem(null, null, "1234567890123", "MFN-1", "Elko", 10.0, "PLN", 5, 2, true, false, false),
                new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 2, "Elko"));
        assertEquals(new InventoryItem(null, null, "1234567890123", "MFN-1", "Elko", 10.0, "PLN", 5, 2, false, false, false),
                new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 2, "Elko", false));
        assertEquals(new InventoryItem(null, null, "1234567890123", "MFN-1", "Elko", 10.0, "PLN", 5, 2, false, true, false),
                new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 2, "Elko", false, true, false));
        assertEquals(new InventoryItem(null, null, "1234567890123", "MFN-1", "Elko", 10.0, "PLN", 5, 2, true, true, false),
                new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 2, "Elko", true, true, false));
        assertEquals(new InventoryItem(null, "101", "1234567890123", "MFN-1", "Elko", 10.0, "PLN", 5, 2, false, true, false),
                new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 2, "Elko", false, true, false, "101"));
        assertEquals(new InventoryItem(null, "101", "1234567890123", "MFN-1", "Elko", 10.0, "PLN", 5, 2, true, true, false),
                new InventoryItem("1234567890123", "MFN-1", 10.0, "PLN", 5, 2, "Elko", true, true, false, "101"));
    }

    @Test
    void uuidDoesNotDependOnTheBrand() {
        // given
        InventoryItem asus = new InventoryItem("ASUS", null, "1234567890123", "MFN-1", "Elko",
                10.0, "PLN", 5, 1, true, false, false);
        InventoryItem msi = new InventoryItem("MSI", null, "1234567890123", "MFN-1", "Elko",
                10.0, "PLN", 5, 1, true, false, false);

        // when / then
        assertEquals(asus.uuid(), msi.uuid());
    }
}
